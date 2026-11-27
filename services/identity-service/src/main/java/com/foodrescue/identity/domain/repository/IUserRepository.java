package com.foodrescue.identity.domain.repository;

import com.foodrescue.identity.domain.model.PageResult;
import com.foodrescue.identity.domain.model.UserAccount;
import com.foodrescue.identity.domain.model.enums.UserRole;
import com.foodrescue.identity.domain.model.enums.UserStatus;
import java.util.Optional;

public interface IUserRepository {
    Optional<UserAccount> findById(Long id);
    Optional<UserAccount> findByEmail(String email);
    Optional<UserAccount> findByGoogleSub(String googleSub);
    UserAccount save(UserAccount user);
    PageResult<UserAccount> search(UserRole role, UserStatus status, String keyword, int page, int size);
    PageResult<UserAccount> findStaffByStoreId(Long storeId, int page, int size);
    long countAll();
    long countActiveCustomers();
}
