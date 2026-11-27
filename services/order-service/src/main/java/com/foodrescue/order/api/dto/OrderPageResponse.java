package com.foodrescue.order.api.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record OrderPageResponse(
        List<OrderResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
    public static OrderPageResponse from(Page<OrderResponse> result) {
        return new OrderPageResponse(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
}
