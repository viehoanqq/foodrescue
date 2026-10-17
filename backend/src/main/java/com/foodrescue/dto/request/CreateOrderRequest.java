package com.foodrescue.dto.request;

import jakarta.validation.Valid;
import java.util.List;

public record CreateOrderRequest(
        @Valid
        List<CreateOrderItemRequest> items) {}
