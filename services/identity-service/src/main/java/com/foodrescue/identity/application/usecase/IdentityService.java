package com.foodrescue.identity.application.usecase;

import com.foodrescue.identity.domain.model.UserAccount;
import com.foodrescue.identity.domain.model.PageResult;
import com.foodrescue.identity.domain.repository.UserRepository;
import com.foodrescue.identity.domain.exception.IdentityException;
import com.foodrescue.identity.application.port.out.AuditPort;
import com.foodrescue.identity.application.port.out.PasswordPort;
import com.foodrescue.identity.application.port.out.TokenPort;
import com.foodrescue.identity.application.port.out.UnitOfWork;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;

public class IdentityService {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserRepository users;
    private final PasswordPort passwords;
    private final TokenPort tokens;
    private final AuditPort audit;
    private final UnitOfWork unitOfWork;

    public IdentityService(UserRepository users, PasswordPort passwords, TokenPort tokens, AuditPort audit,
                           UnitOfWork unitOfWork) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.audit = audit;
        this.unitOfWork = unitOfWork;
    }

    public AuthResult register(String email, String password, String fullName, String phone) {
        return unitOfWork.execute(() -> registerTransaction(email, password, fullName, phone));
    }

    private AuthResult registerTransaction(String email, String password, String fullName, String phone) {
        String normalized = normalize(email);
        if (users.findByEmail(normalized).isPresent()) {
            throw new IdentityException(409, "EMAIL_EXISTS", "Email đã được sử dụng");
        }
        UserAccount saved = users.save(new UserAccount(null, normalized, passwords.hash(password),
                fullName.trim(), phone.trim(), "CUSTOMER", null, "ACTIVE", false,
                LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")), 0));
        audit.record(saved.id(), "USER_REGISTERED", "USER", saved.id(), null);
        return new AuthResult(saved, tokens.issue(saved.id(), saved.tokenVersion()));
    }

    public AuthResult login(String email, String password) {
        UserAccount user = users.findByEmail(normalize(email))
                .orElseThrow(() -> new IdentityException(401, "INVALID_CREDENTIALS", "Email hoặc mật khẩu không đúng"));
        if (!passwords.matches(password, user.passwordHash())) {
            throw new IdentityException(401, "INVALID_CREDENTIALS", "Email hoặc mật khẩu không đúng");
        }
        if (!"ACTIVE".equals(user.status())) {
            throw new IdentityException(403, "ACCOUNT_LOCKED", "Tài khoản đã bị khóa");
        }
        return new AuthResult(user, tokens.issue(user.id(), user.tokenVersion()));
    }

    public UserAccount find(Long id) {
        return users.findById(id).orElseThrow(() -> new IdentityException(404, "NOT_FOUND", "Không tìm thấy người dùng"));
    }

    public UserAccount updateProfile(Long id, String fullName, String phone) {
        return unitOfWork.execute(() -> updateProfileTransaction(id, fullName, phone));
    }

    private UserAccount updateProfileTransaction(Long id, String fullName, String phone) {
        UserAccount old = find(id);
        UserAccount saved = users.save(copy(old, old.passwordHash(), fullName.trim(), phone.trim(),
                old.status(), old.mustChangePassword(), old.tokenVersion()));
        audit.record(id, "USER_PROFILE_UPDATED", "USER", id, null);
        return saved;
    }

    public void changePassword(Long id, String oldPassword, String newPassword) {
        unitOfWork.execute(() -> { changePasswordTransaction(id, oldPassword, newPassword); return null; });
    }

    private void changePasswordTransaction(Long id, String oldPassword, String newPassword) {
        UserAccount old = find(id);
        if (!passwords.matches(oldPassword, old.passwordHash())) {
            throw new IdentityException(422, "WRONG_OLD_PASSWORD", "Mật khẩu hiện tại không đúng");
        }
        users.save(copy(old, passwords.hash(newPassword), old.fullName(), old.phone(),
                old.status(), false, old.tokenVersion() + 1));
        audit.record(id, "USER_PASSWORD_CHANGED", "USER", id, null);
    }

    public PageResult<UserAccount> search(String role, String status, String keyword, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IdentityException(400, "VALIDATION_ERROR", "page hoặc size không hợp lệ");
        }
        return users.search(role, status, keyword, page, size);
    }

    public UserAccount setLocked(Long actorId, Long targetId, boolean locked) {
        return unitOfWork.execute(() -> setLockedTransaction(actorId, targetId, locked));
    }

    private UserAccount setLockedTransaction(Long actorId, Long targetId, boolean locked) {
        UserAccount old = find(targetId);
        if (locked && (targetId.equals(actorId) || "ADMIN".equals(old.role()))) {
            throw new IdentityException(403, "FORBIDDEN", "Không thể khóa tài khoản quản trị");
        }
        String nextStatus = locked ? "LOCKED" : "ACTIVE";
        if (nextStatus.equals(old.status())) return old;
        UserAccount saved = users.save(copy(old, old.passwordHash(), old.fullName(), old.phone(),
                nextStatus, old.mustChangePassword(), old.tokenVersion() + 1));
        audit.record(actorId, locked ? "USER_LOCKED" : "USER_UNLOCKED", "USER", targetId, null);
        return saved;
    }

    public ResetResult resetPassword(Long actorId, Long targetId) {
        return unitOfWork.execute(() -> resetPasswordTransaction(actorId, targetId));
    }

    private ResetResult resetPasswordTransaction(Long actorId, Long targetId) {
        UserAccount old = find(targetId);
        if (targetId.equals(actorId)) {
            throw new IdentityException(403, "FORBIDDEN", "Không thể tự đặt lại mật khẩu tại đây");
        }
        String temporary = randomPassword();
        users.save(copy(old, passwords.hash(temporary), old.fullName(), old.phone(),
                old.status(), true, old.tokenVersion() + 1));
        audit.record(actorId, "USER_PASSWORD_RESET", "USER", targetId, null);
        return new ResetResult(targetId, temporary, true);
    }

    public PageResult<Map<String, Object>> auditLogs(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IdentityException(400, "VALIDATION_ERROR", "page hoặc size không hợp lệ");
        }
        return audit.search(page, size);
    }

    private UserAccount copy(UserAccount old, String passwordHash, String fullName, String phone,
                             String status, boolean mustChangePassword, long version) {
        return new UserAccount(old.id(), old.email(), passwordHash, fullName, phone, old.role(), old.storeId(),
                status, mustChangePassword, old.createdAt(), version);
    }

    private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }

    private String randomPassword() {
        StringBuilder result = new StringBuilder(16);
        for (int i = 0; i < 16; i++) result.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return result.toString();
    }

    public record AuthResult(UserAccount user, String token) {}
    public record ResetResult(Long userId, String temporaryPassword, boolean mustChangePassword) {}
}
