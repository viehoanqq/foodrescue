package com.foodrescue.finance.application.port.out;

import com.foodrescue.finance.domain.model.FinanceModels.*;

import java.util.Map;
import java.util.UUID;

public interface FinanceDependencies {
    Intent intent(long orderId, UUID correlation);

    Account account(long storeId, UUID correlation);

    long ownerStore(String authorization);

    Map<String, Object> counts(UUID correlation);
}
