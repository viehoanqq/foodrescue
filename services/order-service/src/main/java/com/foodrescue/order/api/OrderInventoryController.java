package com.foodrescue.order.api;

import com.foodrescue.buildingblocks.http.InternalKeyVerifier;
import com.foodrescue.buildingblocks.http.InternalServiceException;
import com.foodrescue.order.application.usecase.StockReservationUseCase;
import com.foodrescue.order.domain.model.StockReservation;
import com.foodrescue.order.domain.model.StockReservationCommand;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Internal integration endpoint; not the public checkout API. */
@RestController
@RequestMapping("/internal/order/reservations")
public class OrderInventoryController {
    private final StockReservationUseCase useCase;
    private final InternalKeyVerifier verifier;

    public OrderInventoryController(StockReservationUseCase useCase, @Value("${app.internal-api-key}") String key) {
        this.useCase = useCase;
        this.verifier = new InternalKeyVerifier(key);
    }

    @PostMapping
    public StockReservation reserve(@RequestHeader("X-Internal-Key") String key,
                                    @RequestHeader(value = "X-Correlation-Id", required = false) UUID correlationId,
                                    @Valid @RequestBody StockReservationCommand command) {
        verifier.verify(key);
        try { return useCase.reserve(command, correlationId == null ? command.reservationId() : correlationId); }
        catch (InternalServiceException ex) { throw translate(ex); }
    }

    @GetMapping("/{id}")
    public StockReservation find(@RequestHeader("X-Internal-Key") String key, @PathVariable UUID id) {
        verifier.verify(key);
        try { return useCase.find(id, id); }
        catch (InternalServiceException ex) { throw translate(ex); }
    }

    @PostMapping("/{id}/release")
    public StockReservation release(@RequestHeader("X-Internal-Key") String key, @PathVariable UUID id) {
        verifier.verify(key);
        try { return useCase.release(id, id); }
        catch (InternalServiceException ex) { throw translate(ex); }
    }

    private ResponseStatusException translate(InternalServiceException ex) {
        HttpStatus status = HttpStatus.resolve(ex.status());
        return new ResponseStatusException(status != null && status.is4xxClientError() ? status : HttpStatus.BAD_GATEWAY,
                "Catalog reservation request failed", ex);
    }
}
