package com.foodrescue.catalog.application.port.out;

import java.util.List;

public interface IBatchStorePort {
    Long requireSingleStore(List<Long> batchIds);
}
