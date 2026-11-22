package com.foodrescue.catalog.api;

import com.foodrescue.catalog.application.usecase.ReservationService;
import com.foodrescue.catalog.domain.model.ReservationRequest;
import com.foodrescue.catalog.domain.model.ReservationResult;
import com.foodrescue.buildingblocks.http.InternalKeyVerifier;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/reservations")
public class ReservationController {
    private final ReservationService service;
    private final InternalKeyVerifier verifier;

    public ReservationController(ReservationService service, @Value("${app.internal-api-key}") String internalKey) {
        this.service = service;
        this.verifier = new InternalKeyVerifier(internalKey);
    }

    @PostMapping
    public ReservationResult reserve(@RequestHeader("X-Internal-Key") String key,
                                     @Valid @RequestBody ReservationRequest request) {
        verifier.verify(key);
        return service.reserve(request);
    }

    @GetMapping("/{id}")
    public ReservationResult get(@RequestHeader("X-Internal-Key") String key, @PathVariable UUID id) {
        verifier.verify(key);
        return service.load(id);
    }

    @PostMapping("/{id}/release")
    public ReservationResult release(@RequestHeader("X-Internal-Key") String key, @PathVariable UUID id) {
        verifier.verify(key);
        return service.release(id);
    }
}
