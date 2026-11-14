package com.campusdesk.web;

import com.campusdesk.model.PageResult;
import com.campusdesk.model.Priority;
import com.campusdesk.model.Ticket;
import com.campusdesk.model.TicketQuery;
import com.campusdesk.model.TicketStatus;
import com.campusdesk.security.CurrentUser;
import com.campusdesk.service.TicketService;
import com.campusdesk.service.TicketService.TicketDetail;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService tickets;

    public TicketController(TicketService tickets) {
        this.tickets = tickets;
    }

    /**
     * GET /api/tickets?status=NEW,ASSIGNED&priority=URGENT&category=3&assignee=me|unassigned|12
     *                 &requester=5&q=wifi&overdue=true&sort=priority&page=0&size=50
     * Every parameter is optional. Students always get only their own tickets.
     */
    @GetMapping
    public PageResult<Ticket> search(CurrentUser me,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(required = false) String priority,
                                     @RequestParam(required = false) Long category,
                                     @RequestParam(required = false) String assignee,
                                     @RequestParam(required = false) Long requester,
                                     @RequestParam(required = false) String q,
                                     @RequestParam(defaultValue = "false") boolean overdue,
                                     @RequestParam(defaultValue = "newest") String sort,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "50") int size) {
        TicketQuery.AssigneeMode mode = TicketQuery.AssigneeMode.ANY;
        Long assigneeId = null;
        if (assignee != null && !assignee.isBlank()) {
            switch (assignee.trim().toLowerCase(Locale.ROOT)) {
                case "unassigned" -> mode = TicketQuery.AssigneeMode.UNASSIGNED;
                case "me" -> {
                    mode = TicketQuery.AssigneeMode.USER;
                    assigneeId = me.id();
                }
                default -> {
                    mode = TicketQuery.AssigneeMode.USER;
                    assigneeId = Long.parseLong(assignee.trim());
                }
            }
        }
        TicketQuery query = new TicketQuery(
                parseEnums(status, TicketStatus.class), parseEnums(priority, Priority.class), category,
                mode, assigneeId, requester, q, overdue,
                TicketQuery.Sort.valueOf(sort.toUpperCase(Locale.ROOT)), page, size);
        return tickets.search(me, query);
    }

    /** Sidebar badges for staff: open / unassigned / mine / overdue / waiting. */
    @GetMapping("/counts")
    public Map<String, Object> counts(CurrentUser me) {
        return tickets.queueCounts(me);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketDetail create(CurrentUser me, @RequestBody TicketService.NewTicketRequest body) {
        return tickets.create(me, body);
    }

    @GetMapping("/{id}")
    public TicketDetail get(CurrentUser me, @PathVariable long id) {
        return tickets.detail(me, id);
    }

    @PatchMapping("/{id}/status")
    public TicketDetail status(CurrentUser me, @PathVariable long id, @Valid @RequestBody Requests.StatusChange body) {
        return tickets.changeStatus(me, id, body.status(), body.resolution());
    }

    @PatchMapping("/{id}/priority")
    public TicketDetail priority(CurrentUser me, @PathVariable long id, @Valid @RequestBody Requests.PriorityChange body) {
        return tickets.changePriority(me, id, body.priority());
    }

    @PatchMapping("/{id}/assignee")
    public TicketDetail assign(CurrentUser me, @PathVariable long id, @RequestBody Requests.Assign body) {
        return tickets.assign(me, id, body.assigneeId());
    }

    @PatchMapping("/{id}/category")
    public TicketDetail category(CurrentUser me, @PathVariable long id, @Valid @RequestBody Requests.CategoryChange body) {
        return tickets.changeCategory(me, id, body.categoryId());
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public TicketDetail comment(CurrentUser me, @PathVariable long id, @RequestBody Requests.NewComment body) {
        return tickets.comment(me, id, body.body(), body.internal());
    }

    /** "NEW,in_progress" -> {NEW, IN_PROGRESS}. Generic over any enum type. */
    static <E extends Enum<E>> Set<E> parseEnums(String csv, Class<E> type) {
        if (csv == null || csv.isBlank()) return Set.of();
        EnumSet<E> out = EnumSet.noneOf(type);
        Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(s -> out.add(Enum.valueOf(type, s.toUpperCase(Locale.ROOT))));
        return out;
    }
}
