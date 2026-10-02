package com.foodrescue.common.security;

import com.foodrescue.common.exception.BusinessException;
import com.foodrescue.common.exception.ErrorCode;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Lấy người đang đăng nhập ở bất kỳ đâu trong service / controller.
 * <pre>
 * Long me = CurrentUser.id();
 * Long storeId = CurrentUser.storeId();   // luôn dùng storeId trong token, KHÔNG tin storeId gửi lên
 * </pre>
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /** Người đang đăng nhập; chưa đăng nhập thì rỗng (vd tác vụ tự động, API công khai). */
    public static Optional<AuthUser> find() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    /** Người đang đăng nhập; chưa đăng nhập thì ném UNAUTHORIZED. */
    public static AuthUser get() {
        return find().orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    public static Long id() {
        return get().id();
    }

    /** Cửa hàng của chủ / nhân viên đang đăng nhập; vai trò khác gọi hàm này thì ném FORBIDDEN. */
    public static Long storeId() {
        Long storeId = get().storeId();
        if (storeId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return storeId;
    }
}
