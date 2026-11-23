package com.foodrescue.store.application.port.out;

public interface IStaffIdentityPort {
    CreatedStaff create(Long actorId, Long storeId, NewStaff staff);
    StaffPage list(Long actorId, Long storeId, int page, int size);
    StaffStatus setLocked(Long actorId, Long storeId, Long staffId, boolean locked);

    record NewStaff(String email, String fullName, String phone) {}

    record CreatedStaff(Long userId, String email, String fullName, String phone, String role,
                        Long storeId, String status, boolean mustChangePassword,
                        String temporaryPassword) {}
    record StaffSummary(Long id, String email, String fullName, String phone, String status) {}
    record StaffPage(java.util.List<StaffSummary> content, int page, int size,
                     long totalElements, int totalPages) {}
    record StaffStatus(Long userId, Long storeId, String status, long tokenVersion) {}
}
