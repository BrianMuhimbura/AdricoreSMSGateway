package com.servicecops.project.utils;

import com.alibaba.fastjson2.JSONObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PagedQuery {
    public static final int DEFAULT_ROWS = 10;
    public static final int MAX_ROWS = 100;

    private PagedQuery() {
    }

    public record Request(int first, int rows, String sortField, int sortOrder, String query) {
    }

    public static Request from(JSONObject body, String defaultSortField, int defaultSortOrder) {
        JSONObject source = body == null ? new JSONObject() : body;
        int first = Math.max(source.getIntValue("first"), 0);
        int rows = source.getIntValue("rows");
        if (rows <= 0) {
            rows = DEFAULT_ROWS;
        }
        rows = Math.min(rows, MAX_ROWS);

        String sortField = source.getString("sortField");
        if (!StringUtils.hasText(sortField)) {
            sortField = defaultSortField;
        }

        int sortOrder = source.containsKey("sortOrder") ? source.getIntValue("sortOrder") : defaultSortOrder;
        String query = source.getString("query");
        if (query != null) {
            query = query.trim();
        }
        return new Request(first, rows, sortField.trim(), sortOrder, query);
    }

    public static Pageable toPageable(Request request, Set<String> allowedSortFields, String fallbackSort) {
        String field = allowedSortFields.contains(request.sortField()) ? request.sortField() : fallbackSort;
        Sort.Direction direction = request.sortOrder() > 0 ? Sort.Direction.ASC : Sort.Direction.DESC;
        if (request.sortOrder() == 0) {
            field = fallbackSort;
            direction = Sort.Direction.DESC;
        }
        int page = request.rows() == 0 ? 0 : request.first() / request.rows();
        return PageRequest.of(page, request.rows(), Sort.by(direction, field));
    }

    public static Map<String, Object> payload(List<?> items, Page<?> page, Request request) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("totalRecords", page.getTotalElements());
        data.put("first", request.first());
        data.put("rows", request.rows());
        return data;
    }
}
