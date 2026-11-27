package com.foodrescue.catalog.application.usecase;

import com.foodrescue.catalog.application.port.out.IInboxPort;
import com.foodrescue.catalog.application.port.out.IStoreProjectionRepository;
import com.foodrescue.catalog.domain.model.StoreStatusChange;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Cập nhật projection cửa hàng từ store.status-changed.v1; Inbox và projection cùng một transaction. */
@Service
public class StoreStatusProjectionUseCase {
    public static final String CONSUMER_NAME = "catalog.store-status";

    private final IInboxPort inbox;
    private final IStoreProjectionRepository projection;

    public StoreStatusProjectionUseCase(IInboxPort inbox, IStoreProjectionRepository projection) {
        this.inbox = inbox;
        this.projection = projection;
    }

    @Transactional
    public void apply(UUID eventId, StoreStatusChange change) {
        if (!inbox.markProcessed(CONSUMER_NAME, eventId)) return;
        projection.applyIfNewer(change);
    }
}
