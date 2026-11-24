package com.foodrescue.identity.infrastructure.adapter.out.persistence;

import com.foodrescue.identity.domain.model.PageResult;
import com.foodrescue.identity.domain.repository.IUserRepository;
import com.foodrescue.identity.domain.model.UserAccount;
import com.foodrescue.identity.infrastructure.adapter.out.persistence.entity.UserEntity;
import com.foodrescue.identity.infrastructure.adapter.out.persistence.ISpringDataUserRepository;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
public class JpaUserRepository implements IUserRepository {
    private final ISpringDataUserRepository repository;

    public JpaUserRepository(ISpringDataUserRepository repository) { this.repository = repository; }

    @Override public Optional<UserAccount> findById(Long id) { return repository.findById(id).map(this::toDomain); }
    @Override public Optional<UserAccount> findByEmail(String email) {
        return repository.findByEmailIgnoreCase(email).map(this::toDomain);
    }

    @Override public long countAll() { return repository.count(); }

    @Override public long countActiveCustomers() {
        return repository.countByRoleAndStatus("CUSTOMER", "ACTIVE");
    }

    @Override public UserAccount save(UserAccount user) {
        UserEntity entity = user.id() == null ? new UserEntity() : repository.findById(user.id()).orElseThrow();
        entity.setRole(user.role());
        entity.setEmail(user.email());
        entity.setPasswordHash(user.passwordHash());
        entity.setFullName(user.fullName());
        entity.setPhone(user.phone());
        entity.setAddress(user.address());
        entity.setLatitude(user.latitude());
        entity.setLongitude(user.longitude());
        entity.setStoreId(user.storeId());
        entity.setStatus(user.status());
        entity.setMustChangePassword(user.mustChangePassword());
        entity.setCreatedAt(user.createdAt());
        entity.setTokenVersion(user.tokenVersion());
        return toDomain(repository.saveAndFlush(entity));
    }

    @Override public PageResult<UserAccount> search(String role, String status, String keyword, int page, int size) {
        Specification<UserEntity> spec = (root, query, cb) -> cb.conjunction();
        if (role != null && !role.isBlank()) spec = spec.and((r, q, cb) -> cb.equal(r.get("role"), role));
        if (status != null && !status.isBlank()) spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), status));
        if (keyword != null && !keyword.isBlank()) {
            String value = "%" + keyword.trim().toLowerCase() + "%";
            spec = spec.and((r, q, cb) -> cb.or(
                    cb.like(cb.lower(r.get("email")), value),
                    cb.like(cb.lower(r.get("fullName")), value),
                    cb.like(r.get("phone"), value)));
        }
        Page<UserAccount> found = repository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))).map(this::toDomain);
        return new PageResult<>(found.getContent(), page, size, found.getTotalElements(), found.getTotalPages());
    }

    @Override public PageResult<UserAccount> findStaffByStoreId(Long storeId, int page, int size) {
        Page<UserAccount> found = repository.findByRoleAndStoreId("STORE_STAFF", storeId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))).map(this::toDomain);
        return new PageResult<>(found.getContent(), page, size, found.getTotalElements(), found.getTotalPages());
    }

    private UserAccount toDomain(UserEntity e) {
        return new UserAccount(e.getId(), e.getEmail(), e.getPasswordHash(), e.getFullName(), e.getPhone(),
                e.getRole(), e.getStoreId(), e.getStatus(), Boolean.TRUE.equals(e.getMustChangePassword()),
                e.getCreatedAt(), e.getTokenVersion() == null ? 0 : e.getTokenVersion(),
                e.getAddress(), e.getLatitude(), e.getLongitude());
    }
}
