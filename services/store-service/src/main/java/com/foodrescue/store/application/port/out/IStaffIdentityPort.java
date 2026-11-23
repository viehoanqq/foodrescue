package com.foodrescue.store.application.port.out;

public interface IStaffIdentityPort {
    CreatedStaff create(Long actorId, Long storeId, NewStaff staff);

    record NewStaff(String email, String fullName, String phone) {}

    record CreatedStaff(Long userId, String email, String fullName, String phone, String role,
                        Long storeId, String status, boolean mustChangePassword,
                        String temporaryPassword) {}
}
