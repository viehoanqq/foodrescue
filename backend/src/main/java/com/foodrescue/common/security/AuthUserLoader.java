package com.foodrescue.common.security;

import com.foodrescue.common.entity.Store;
import com.foodrescue.common.entity.User;
import com.foodrescue.common.enums.Role;
import com.foodrescue.common.enums.UserStatus;
import com.foodrescue.common.repository.StoreRepository;
import com.foodrescue.common.repository.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Đọc người dùng từ CSDL và chuyển thành AuthUser (kèm storeId đúng theo vai trò). */
@Service
public class AuthUserLoader {

    private final UserRepository userRepository;
    private final StoreRepository storeRepository;

    public AuthUserLoader(UserRepository userRepository, StoreRepository storeRepository) {
        this.userRepository = userRepository;
        this.storeRepository = storeRepository;
    }

    @Transactional(readOnly = true)
    public Optional<LoadedUser> loadById(Long userId) {
        return userRepository.findById(userId).map(this::toLoaded);
    }

    private LoadedUser toLoaded(User u) {
        Long storeId = null;
        if (u.getRole() == Role.STORE_STAFF && u.getStore() != null) {
            storeId = u.getStore().getId();
        } else if (u.getRole() == Role.STORE_OWNER) {
            storeId = storeRepository.findByOwnerId(u.getId()).map(Store::getId).orElse(null);
        }
        AuthUser authUser = new AuthUser(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), storeId,
                u.isMustChangePassword());
        return new LoadedUser(authUser, u.getStatus());
    }

    /** AuthUser + trạng thái tài khoản (để filter chặn tài khoản bị khoá). */
    public record LoadedUser(AuthUser user, UserStatus status) {
    }
}
