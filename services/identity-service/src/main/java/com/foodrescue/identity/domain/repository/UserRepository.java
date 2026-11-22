package com.foodrescue.identity.domain.repository;

import com.foodrescue.identity.domain.model.UserAccount;
import com.foodrescue.identity.domain.model.PageResult;
import java.util.Optional;

public interface UserRepository {
    Optional<UserAccount> findById(Long id);
    Optional<UserAccount> findByEmail(String email);
    UserAccount save(UserAccount user);
    PageResult<UserAccount> search(String role, String status, String keyword, int page, int size);
    long countAll();
    long countActiveCustomers();
}
