package com.foodrescue.common.api;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Định dạng danh sách có phân trang của mọi API.
 * <pre>{ "content": [...], "page": 0, "size": 20, "totalElements": 135, "totalPages": 7 }</pre>
 * Không trả thẳng {@code Page} của Spring (JSON của nó dài và có thể đổi giữa các phiên bản).
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> PageResponse<T> of(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
