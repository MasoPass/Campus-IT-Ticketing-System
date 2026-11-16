package com.campusdesk.model;

import java.util.Set;

public record TicketQuery(
        Set<TicketStatus> statuses,
        Set<Priority> priorities,
        Long categoryId,
        AssigneeMode assigneeMode,
        Long assigneeId,
        Long requesterId,
        String text,
        boolean overdueOnly,
        Sort sort,
        int page,
        int size
) {
    public enum AssigneeMode { ANY, UNASSIGNED, USER }

    public enum Sort { NEWEST, OLDEST, PRIORITY, DUE, UPDATED }

    public static final int MAX_PAGE_SIZE = 200;

    public TicketQuery {
        statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
        priorities = priorities == null ? Set.of() : Set.copyOf(priorities);
        assigneeMode = assigneeMode == null ? AssigneeMode.ANY : assigneeMode;
        sort = sort == null ? Sort.NEWEST : sort;
        page = Math.max(0, page);
        size = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
    }

    // forces requester (students only see their own)
    public TicketQuery forRequester(long id) {
        return new TicketQuery(statuses, priorities, categoryId, assigneeMode, assigneeId, id, text,
                overdueOnly, sort, page, size);
    }
}
