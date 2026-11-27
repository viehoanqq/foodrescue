package com.foodrescue.order.application.usecase;

import com.foodrescue.order.api.dto.OrderPageResponse;
import com.foodrescue.order.application.port.out.IInventoryPort;
import com.foodrescue.order.application.port.out.IOutboxPort;
import com.foodrescue.order.domain.exception.OrderException;
import com.foodrescue.order.domain.model.enums.OrderStatus;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderItemRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.SpringDataOrderRepository;
import com.foodrescue.order.infrastructure.adapter.out.persistence.entity.OrderEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderListPagingTest {
    private final SpringDataOrderRepository orders = mock(SpringDataOrderRepository.class);
    private final SpringDataOrderItemRepository items = mock(SpringDataOrderItemRepository.class);
    private final IInventoryPort inventory = mock(IInventoryPort.class);
    private final IOutboxPort outbox = mock(IOutboxPort.class);

    @Test
    void customerListUsesCustomerScopeAndReturnsPageMetadata() {
        CustomerOrderUseCase useCase = new CustomerOrderUseCase(orders, items, inventory, outbox);
        when(orders.findCustomerOrders(eq(7L), eq(OrderStatus.CONFIRMED), any(Pageable.class)))
                .thenReturn(new PageImpl<OrderEntity>(List.of(), PageRequest.of(1, 5), 8));

        OrderPageResponse result = useCase.getMyOrders(7L, " confirmed ", 1, 5);

        assertEquals(1, result.page());
        assertEquals(5, result.size());
        assertEquals(8, result.totalElements());
        assertEquals(2, result.totalPages());
    }

    @Test
    void storeDateFiltersCreatedAtWithinRequestedDay() {
        StorePickupUseCase useCase = new StorePickupUseCase(orders, items, outbox);
        LocalDateTime from = LocalDate.of(2026, 11, 27).atStartOfDay();
        when(orders.findStoreOrders(eq(9L), eq(OrderStatus.COMPLETED), eq(from), eq(from.plusDays(1)), any(Pageable.class)))
                .thenReturn(new PageImpl<OrderEntity>(List.of(), PageRequest.of(0, 20), 0));

        OrderPageResponse result = useCase.getStoreOrders(9L, LocalDate.of(2026, 11, 27), "completed", 0, 20);

        assertEquals(0, result.totalElements());
        verify(orders).findStoreOrders(eq(9L), eq(OrderStatus.COMPLETED), eq(from), eq(from.plusDays(1)), any(Pageable.class));
    }

    @Test
    void adminCanFilterByCodeAndInvalidPageSizeOrStatusIsRejected() {
        CustomerOrderUseCase useCase = new CustomerOrderUseCase(orders, items, inventory, outbox);
        when(orders.findAdminOrders(eq(null), eq("FR-123"), any(Pageable.class)))
                .thenReturn(new PageImpl<OrderEntity>(List.of(), PageRequest.of(0, 20), 0));

        useCase.getAllOrdersForAdmin(" fr-123 ", null, 0, 20);
        verify(orders).findAdminOrders(eq(null), eq("FR-123"), any(Pageable.class));
        assertThrows(OrderException.class, () -> useCase.getMyOrders(7L, null, 0, 101));
        OrderException badStatus = assertThrows(OrderException.class,
                () -> useCase.getMyOrders(7L, "UNKNOWN_STATUS", 0, 20));
        assertEquals(400, badStatus.status());
    }
}
