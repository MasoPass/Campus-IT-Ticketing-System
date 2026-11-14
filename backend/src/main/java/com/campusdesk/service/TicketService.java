package com.campusdesk.service;

import com.campusdesk.error.ApiException;
import com.campusdesk.model.Category;
import com.campusdesk.model.Comment;
import com.campusdesk.model.Comment.Kind;
import com.campusdesk.model.Comment.Visibility;
import com.campusdesk.model.DeviceType;
import com.campusdesk.model.PageResult;
import com.campusdesk.model.Priority;
import com.campusdesk.model.Role;
import com.campusdesk.model.SlaPolicy;
import com.campusdesk.model.Ticket;
import com.campusdesk.model.TicketQuery;
import com.campusdesk.model.TicketStatus;
import com.campusdesk.model.User;
import com.campusdesk.repo.CommentRepository;
import com.campusdesk.repo.SearchIndexRepository;
import com.campusdesk.repo.SettingsRepository;
import com.campusdesk.repo.TicketRepository;
import com.campusdesk.repo.UserRepository;
import com.campusdesk.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Everything that happens to a ticket. Each public method follows the same pattern:
 *   1. load the ticket and check this user may see it (404 if not - we don't even confirm it exists)
 *   2. check this user may do this action (403 otherwise)
 *   3. in ONE transaction: change the ticket + add a timeline event
 *   4. refresh the search index
 */
@Service
public class TicketService {

    public static final int MAX_TITLE = 150;
    public static final int MAX_TEXT = 10_000;

    /** What the ticket page needs: the ticket, its visible timeline, and what this user may do. */
    public record TicketDetail(Ticket ticket, List<Comment> timeline, Set<TicketStatus> allowedStatuses,
                               boolean canAssign, boolean canTake, boolean canChangePriority,
                               boolean canChangeCategory, boolean canWriteInternal) {}

    public record NewTicketRequest(String title, String description, Long categoryId, DeviceType deviceType,
                                   String deviceDetails, String building, String room) {}

    private final TicketRepository tickets;
    private final CommentRepository comments;
    private final UserRepository users;
    private final SettingsRepository settings;
    private final SearchIndexRepository search;
    private final TransactionTemplate tx;
    private final Clock clock;

    public TicketService(TicketRepository tickets, CommentRepository comments, UserRepository users,
                         SettingsRepository settings, SearchIndexRepository search, TransactionTemplate tx, Clock clock) {
        this.tickets = tickets;
        this.comments = comments;
        this.users = users;
        this.settings = settings;
        this.search = search;
        this.tx = tx;
        this.clock = clock;
    }

    // ================================================================== reading

    public PageResult<Ticket> search(CurrentUser me, TicketQuery query) {
        // Students only ever see their own tickets, whatever filters they send.
        TicketQuery q = me.isStaff() ? query : query.forRequester(me.id());
        Instant now = clock.instant();
        PageResult<Ticket> page = tickets.search(q, Times.format(now));
        return new PageResult<>(page.items().stream().map(t -> withSla(t, now)).toList(),
                page.page(), page.size(), page.total());
    }

    public TicketDetail detail(CurrentUser me, long id) {
        Ticket t = visibleTicket(me, id);
        boolean isAssignee = me.id() == Objects.requireNonNullElse(t.assigneeId(), -1L);
        return new TicketDetail(
                withSla(t, clock.instant()),
                comments.forTicket(id, me.isStaff()),
                TicketWorkflow.allowedFor(t.status(), me.role(), t.requesterId() == me.id(), isAssignee),
                me.isAdmin() && t.status().isOpen(),
                me.role() == Role.TECHNICIAN && t.assigneeId() == null && t.status().isOpen(),
                me.isAdmin() && t.status().isOpen(),
                me.isStaff() && t.status().isOpen(),
                me.isStaff());
    }

    public Map<String, Object> queueCounts(CurrentUser me) {
        me.requireStaff();
        return tickets.queueCounts(me.id(), Times.format(clock.instant()));
    }

    // ================================================================== creating

    public TicketDetail create(CurrentUser me, NewTicketRequest r) {
        String title = Validation.text(r.title(), "Title", MAX_TITLE, true);
        String description = Validation.text(r.description(), "Description", MAX_TEXT, true);
        String building = Validation.text(r.building(), "Building", 80, true);
        String room = Validation.text(r.room(), "Room", 40, false);
        String deviceDetails = Validation.text(r.deviceDetails(), "Device details", 200, false);
        if (r.deviceType() == null) throw ApiException.badRequest("Choose a device type");
        if (r.categoryId() == null) throw ApiException.badRequest("Choose a category");
        Category category = settings.findCategory(r.categoryId())
                .filter(Category::active)
                .orElseThrow(() -> ApiException.badRequest("Unknown category"));

        // New tickets start at MEDIUM; IT re-prioritises during triage.
        Priority priority = Priority.MEDIUM;
        Instant now = clock.instant();
        SlaCalculator.Deadlines due = SlaCalculator.deadlines(now, settings.slaFor(priority));
        String nowText = Times.format(now);

        long id = Objects.requireNonNull(tx.execute(s -> {
            long newId = tickets.insert(new TicketRepository.NewTicket(title, description, category.id(), priority,
                    me.id(), r.deviceType(), deviceDetails, building, room, nowText,
                    Times.format(due.responseDue()), Times.format(due.resolutionDue())));
            comments.insert(newId, me.id(), Kind.EVENT, Visibility.PUBLIC, me.fullName() + " opened this ticket", nowText);
            return newId;
        }));
        reindex(id);
        return detail(me, id);
    }

    // ================================================================== changing

    /**
     * Status change, checked against the state machine (TicketWorkflow).
     * A technician starting a NEW ticket picks it up: it becomes assigned to them.
     */
    public TicketDetail changeStatus(CurrentUser me, long id, TicketStatus to, String resolutionText) {
        Ticket t = visibleTicket(me, id);
        boolean isAssignee = me.id() == Objects.requireNonNullElse(t.assigneeId(), -1L);
        if (!TicketWorkflow.canMove(t.status(), to, me.role(), t.requesterId() == me.id(), isAssignee)) {
            throw ApiException.forbidden("You can't move this ticket from " + t.status().label() + " to " + to.label());
        }
        String resolution = Validation.text(resolutionText, "Resolution", MAX_TEXT, false);
        if (TicketWorkflow.requiresResolution(t.status(), to) && resolution == null) {
            throw ApiException.badRequest(to == TicketStatus.RESOLVED
                    ? "Describe how the issue was resolved"
                    : "Give a short reason for closing this ticket");
        }
        String now = Times.format(clock.instant());

        tx.executeWithoutResult(s -> {
            if (t.status() == TicketStatus.NEW && to == TicketStatus.IN_PROGRESS && t.assigneeId() == null && me.isStaff()) {
                tickets.updateAssignee(id, me.id(), TicketStatus.ASSIGNED, now);
                comments.insert(id, me.id(), Kind.EVENT, Visibility.PUBLIC, me.fullName() + " picked up this ticket", now);
            }
            tickets.updateStatus(id, to, resolution, now);
            if (me.isStaff()) tickets.markFirstResponse(id, now);
            comments.insert(id, me.id(), Kind.EVENT, Visibility.PUBLIC, statusEvent(me, t.status(), to, resolution), now);
        });
        reindex(id);
        return detail(me, id);
    }

    /** Admins assign to anyone on staff (or unassign); technicians can only take unassigned tickets. */
    public TicketDetail assign(CurrentUser me, long id, Long assigneeId) {
        me.requireStaff();
        Ticket t = visibleTicket(me, id);
        if (!t.status().isOpen()) throw ApiException.conflict("Reopen the ticket before reassigning it");

        if (me.role() == Role.TECHNICIAN) {
            boolean takingIt = assigneeId != null && assigneeId == me.id() && t.assigneeId() == null;
            if (!takingIt) throw ApiException.forbidden("Technicians can only take unassigned tickets");
        }
        User assignee = null;
        if (assigneeId != null) {
            assignee = users.findById(assigneeId)
                    .filter(u -> u.active() && u.role().isStaff())
                    .orElseThrow(() -> ApiException.badRequest("That person can't be assigned tickets"));
        }
        if (Objects.equals(assigneeId, t.assigneeId())) return detail(me, id);

        // Keep status and assignment consistent: NEW <-> ASSIGNED follow the assignee.
        TicketStatus status = t.status();
        if (assigneeId != null && status == TicketStatus.NEW) status = TicketStatus.ASSIGNED;
        if (assigneeId == null && status == TicketStatus.ASSIGNED) status = TicketStatus.NEW;

        String now = Times.format(clock.instant());
        String event = assignee == null ? me.fullName() + " unassigned this ticket"
                : assignee.id() == me.id() ? me.fullName() + " took this ticket"
                : me.fullName() + " assigned this ticket to " + assignee.fullName();
        TicketStatus finalStatus = status;
        tx.executeWithoutResult(s -> {
            tickets.updateAssignee(id, assigneeId, finalStatus, now);
            comments.insert(id, me.id(), Kind.EVENT, Visibility.PUBLIC, event, now);
        });
        return detail(me, id);
    }

    /**
     * Admin-only. The SLA deadlines are recalculated from the ORIGINAL creation time with the
     * new priority's targets, so raising a 2-day-old ticket to URGENT makes it overdue at once.
     * Logged as an INTERNAL event: students don't see priority juggling.
     */
    public TicketDetail changePriority(CurrentUser me, long id, Priority priority) {
        me.require(Role.ADMIN);
        Ticket t = visibleTicket(me, id);
        if (!t.status().isOpen()) throw ApiException.conflict("Closed and resolved tickets keep their priority");
        if (t.priority() == priority) return detail(me, id);
        SlaPolicy policy = settings.slaFor(priority);
        SlaCalculator.Deadlines due = SlaCalculator.deadlines(Times.parse(t.createdAt()), policy);
        String now = Times.format(clock.instant());
        tx.executeWithoutResult(s -> {
            tickets.updatePriority(id, priority, Times.format(due.responseDue()), Times.format(due.resolutionDue()), now);
            comments.insert(id, me.id(), Kind.EVENT, Visibility.INTERNAL,
                    me.fullName() + " changed priority from " + label(t.priority()) + " to " + label(priority), now);
        });
        return detail(me, id);
    }

    public TicketDetail changeCategory(CurrentUser me, long id, long categoryId) {
        me.requireStaff();
        Ticket t = visibleTicket(me, id);
        Category c = settings.findCategory(categoryId).filter(Category::active)
                .orElseThrow(() -> ApiException.badRequest("Unknown category"));
        if (c.id() == t.categoryId()) return detail(me, id);
        String now = Times.format(clock.instant());
        tx.executeWithoutResult(s -> {
            tickets.updateCategory(id, c.id(), now);
            comments.insert(id, me.id(), Kind.EVENT, Visibility.INTERNAL,
                    me.fullName() + " moved this ticket to " + c.name(), now);
        });
        return detail(me, id);
    }

    /**
     * Add a reply (visible to the student) or an internal note (IT only).
     * - A staff reply counts as the "first response" for the SLA.
     * - A student replying to a ticket that was waiting on them puts it back to In progress.
     */
    public TicketDetail comment(CurrentUser me, long id, String body, boolean internal) {
        Ticket t = visibleTicket(me, id);
        String text = Validation.text(body, "Message", MAX_TEXT, true);
        if (internal && !me.isStaff()) throw ApiException.forbidden("Only IT staff can add internal notes");
        if (t.status() == TicketStatus.CLOSED && !me.isStaff()) {
            throw ApiException.conflict("This ticket is closed - open a new ticket if the problem is back");
        }
        String now = Times.format(clock.instant());
        tx.executeWithoutResult(s -> {
            comments.insert(id, me.id(), Kind.MESSAGE, internal ? Visibility.INTERNAL : Visibility.PUBLIC, text, now);
            if (me.isStaff() && !internal) tickets.markFirstResponse(id, now);
            else tickets.touch(id, now);
            if (!me.isStaff() && t.status() == TicketStatus.WAITING_ON_USER) {
                tickets.updateStatus(id, TicketStatus.IN_PROGRESS, null, now);
                comments.insert(id, me.id(), Kind.EVENT, Visibility.PUBLIC,
                        "Status changed to In progress (student replied)", now);
            }
        });
        reindex(id);
        return detail(me, id);
    }

    // ================================================================== helpers

    /**
     * Loads a ticket the user is allowed to see. For a student asking for someone else's
     * ticket we answer 404, not 403: a 403 would confirm that ticket number exists.
     */
    private Ticket visibleTicket(CurrentUser me, long id) {
        Ticket t = tickets.findById(id).orElseThrow(() -> ApiException.notFound("Ticket", id));
        if (!me.isStaff() && t.requesterId() != me.id()) throw ApiException.notFound("Ticket", id);
        return t;
    }

    private static Ticket withSla(Ticket t, Instant now) {
        return t.withSla(SlaCalculator.state(Times.parse(t.createdAt()), Times.parse(t.resolutionDueAt()),
                Times.parse(t.resolvedAt()), now));
    }

    private void reindex(long id) {
        Ticket t = tickets.findById(id).orElseThrow();
        List<String> parts = new ArrayList<>(List.of(t.title(), t.description(), t.building()));
        if (t.room() != null) parts.add(t.room());
        if (t.deviceDetails() != null) parts.add(t.deviceDetails());
        if (t.resolution() != null) parts.add(t.resolution());
        parts.add(t.requesterName());
        parts.addAll(comments.messageBodies(id));
        search.upsert(id, String.join("\n", parts));
    }

    private static String statusEvent(CurrentUser me, TicketStatus from, TicketStatus to, String resolution) {
        if (me.role() == Role.STUDENT && from == TicketStatus.RESOLVED && to == TicketStatus.CLOSED) {
            return me.fullName() + " confirmed the issue is fixed";
        }
        if (me.role() == Role.STUDENT && to == TicketStatus.IN_PROGRESS) {
            return me.fullName() + " reopened the ticket (still not fixed)";
        }
        if (me.role() == Role.STUDENT && to == TicketStatus.CLOSED) {
            return me.fullName() + " withdrew this ticket: " + resolution;
        }
        if (from == TicketStatus.CLOSED || from == TicketStatus.RESOLVED) {
            return me.fullName() + " reopened the ticket";
        }
        return me.fullName() + " changed status to " + to.label();
    }

    private static String label(Priority p) {
        return p.name().charAt(0) + p.name().substring(1).toLowerCase();
    }
}
