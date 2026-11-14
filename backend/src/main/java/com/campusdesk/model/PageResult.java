package com.campusdesk.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** One page of results plus the total, so the UI can draw "page 2 of 7". */
public record PageResult<T>(List<T> items, int page, int size, long total) {
    @JsonProperty("totalPages")
    public int totalPages() {
        return size == 0 ? 0 : (int) ((total + size - 1) / size);   // ceiling division
    }
}
