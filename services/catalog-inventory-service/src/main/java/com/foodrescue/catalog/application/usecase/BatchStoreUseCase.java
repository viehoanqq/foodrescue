package com.foodrescue.catalog.application.usecase;

import com.foodrescue.catalog.application.port.out.IBatchStorePort;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class BatchStoreUseCase {
    private final IBatchStorePort batches;

    public BatchStoreUseCase(IBatchStorePort batches) {
        this.batches = batches;
    }

    public Long requireSingleStore(List<Long> batchIds) {
        return batches.requireSingleStore(batchIds);
    }
}
