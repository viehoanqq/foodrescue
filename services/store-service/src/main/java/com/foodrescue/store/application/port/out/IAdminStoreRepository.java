package com.foodrescue.store.application.port.out;

import com.foodrescue.store.domain.model.StoreView;
import com.foodrescue.store.domain.model.StoreProfileUpdate;
import java.math.BigDecimal;
import java.util.Optional;

public interface IAdminStoreRepository {
    StoreView.AdminPage findForAdmin(String status, int page, int size);
    Optional<StoreView.Admin> findForAdminById(Long id);
    StoreView.Admin changeStatus(Long id, String expectedStatus, String nextStatus,
                                 BigDecimal commissionRate, String rejectReason, Long actorId);
    StoreView.Admin changeCommission(Long id, BigDecimal commissionRate, Long actorId);
    StoreView.Admin updateProfile(Long id, StoreProfileUpdate input, Long actorId);
}
