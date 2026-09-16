package com.billing.simple.billsoft.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PaginationUtils {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 25;
    public static final int MAX_SIZE = 100;

    private PaginationUtils() {}

    public static Pageable createPageRequest(int page, int size, Sort sort) {
        int validPage = Math.max(0, page);
        int validSize = Math.max(1, Math.min(size <= 0 ? DEFAULT_SIZE : size, MAX_SIZE));
        return PageRequest.of(validPage, validSize, sort);
    }

    public static Pageable createDefaultTransactionPageRequest(int page, int size, String dateFieldName) {
        Sort sort = Sort.by(Sort.Order.desc(dateFieldName), Sort.Order.desc("id"));
        return createPageRequest(page, size, sort);
    }
}
