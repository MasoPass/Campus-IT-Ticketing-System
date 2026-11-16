package com.campusdesk.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record PageResult<T>(List<T> items, int page, int size, long total) {
    @JsonProperty("totalPages")
    public int totalPages() {
        return size == 0 ? 0 : (int) ((total + size - 1) / size); // ceiling division
    }
}
