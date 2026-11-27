package com.foodrescue.identity.application.usecase;

import com.foodrescue.identity.domain.model.UserAccount;
import com.foodrescue.identity.domain.model.PageResult;
import com.foodrescue.identity.domain.model.enums.OtpType;
import com.foodrescue.identity.domain.model.enums.UserRole;
import com.foodrescue.identity.domain.model.enums.UserStatus;
import com.foodrescue.identity.domain.repository.IUserRepository;
import com.foodrescue.identity.domain.exception.IdentityException;
import com.foodrescue.identity.application.port.out.IAuditRepository;
import com.foodrescue.identity.application.port.out.IPasswordPort;
import com.foodrescue.identity.application.port.out.ITokenPort;
import com.foodrescue.identity.application.port.out.IUnitOfWork;
import com.foodrescue.identity.application.port.out.IEmailOtpPort;
import com.foodrescue.identity.application.port.out.IGoogleIdentityPort;
import java.security.SecureRandom;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Map;

public class IdentityService {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final IUserRepository users;
    private final IPasswordPort passwords;
    private final ITokenPort tokens;
    private final IAuditRepository audit;
    private final IUnitOfWork unitOfWork;
    private final IGoogleIdentityPort google;
    private final IEmailOtpPort emailOtp;

    public IdentityService(IUserRepository users, IPasswordPort passwords, ITokenPort tokens, IAuditRepository audit,
                           IUnitOfWork unitOfWork, IGoogleIdentityPort google, IEmailOtpPort emailOtp) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.audit = audit;
        this.unitOfWork = unitOfWork;
        this.google = google;
        this.emailOtp = emailOtp;
    }

    public void sendRegistrationOtp(String email) {
        String normalized = normalize(email);
        if (users.findByEmail(normalized).isPresent())
            throw new IdentityException(409, "EMAIL_EXISTS", "Email đã được sử dụng");
        emailOtp.send(OtpType.REGISTRATION, normalized);
    }

    public void sendPasswordResetOtp(String email) {
        String normalized = normalize(email);
        findActiveUserByEmail(normalized);
        emailOtp.send(OtpType.PASSWORD_RESET, normalized);
    }

    public void resetPasswordWithOtp(String email, String otp, String newPassword) {
        String normalized = normalize(email);
        findActiveUserByEmail(normalized);
        emailOtp.verify(OtpType.PASSWORD_RESET, normalized, otp);
        unitOfWork.execute(() -> {
            resetPasswordWithOtpTransaction(normalized, otp, newPassword);
            return null;
        });
    }

    private void resetPasswordWithOtpTransaction(String email, String otp, String newPassword) {
        UserAccount user = findActiveUserByEmail(email);
        emailOtp.consume(OtpType.PASSWORD_RESET, email, otp);
        users.save(copy(user, passwords.hash(newPassword), user.fullName(), user.phone(),
                user.status(), false, user.tokenVersion() + 1));
        audit.record(user.id(), "USER_PASSWORD_RESET", "USER", user.id(), "EMAIL_OTP");
    }

    private UserAccount findActiveUserByEmail(String normalizedEmail) {
        UserAccount user = users.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IdentityException(404, "EMAIL_NOT_FOUND",
                        "Không tìm thấy tài khoản với email này"));
        if (!user.status().isActive()) {
            throw new IdentityException(403, "ACCOUNT_LOCKED", "Tài khoản đã bị khóa");
        }
        return user;
    }

    public AuthResult register(String email, String password, String fullName, String phone, String otp) {
        String normalized = normalize(email);
        emailOtp.verify(OtpType.REGISTRATION, normalized, otp);
        return unitOfWork.execute(() -> registerTransaction(normalized, password, fullName, phone, otp));
    }

    private AuthResult registerTransaction(String email, String password, String fullName, String phone, String otp) {
        String normalized = normalize(email);
        if (users.findByEmail(normalized).isPresent()) {
            throw new IdentityException(409, "EMAIL_EXISTS", "Email đã được sử dụng");
        }
        emailOtp.consume(OtpType.REGISTRATION, normalized, otp);
        UserAccount saved = users.save(new UserAccount(null, normalized, passwords.hash(password),
                fullName.trim(), phone.trim(), UserRole.CUSTOMER, null, UserStatus.ACTIVE, false,
                LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")), 0));
        audit.record(saved.id(), "USER_REGISTERED", "USER", saved.id(), null);
        return new AuthResult(saved, tokens.issue(saved.id(), saved.tokenVersion()));
    }

    public AuthResult login(String email, String password) {
        UserAccount user = users.findByEmail(normalize(email))
                .orElseThrow(() -> new IdentityException(401, "INVALID_CREDENTIALS", "Email hoặc mật khẩu không đúng"));
        if (user.passwordHash() == null || !passwords.matches(password, user.passwordHash())) {
            throw new IdentityException(401, "INVALID_CREDENTIALS", "Email hoặc mật khẩu không đúng");
        }
        if (!user.status().isActive()) {
            throw new IdentityException(403, "ACCOUNT_LOCKED", "Tài khoản đã bị khóa");
        }
        return new AuthResult(user, tokens.issue(user.id(), user.tokenVersion()));
    }

    public AuthResult loginWithGoogle(String idToken) {
        IGoogleIdentityPort.GoogleProfile profile = google.verify(idToken);
        return unitOfWork.execute(() -> loginWithGoogleTransaction(profile));
    }

    private AuthResult loginWithGoogleTransaction(IGoogleIdentityPort.GoogleProfile profile) {
        UserAccount user = users.findByGoogleSub(profile.sub()).orElseGet(() -> {
            String email = normalize(profile.email());
            if (users.findByEmail(email).isPresent()) {
                throw new IdentityException(409, "GOOGLE_LINK_REQUIRED",
                        "Email đã có tài khoản. Hãy đăng nhập bằng mật khẩu để liên kết Google sau.");
            }
            String name = profile.fullName() == null || profile.fullName().isBlank()
                    ? email : profile.fullName().trim();
            UserAccount created = users.save(new UserAccount(null, email, null, name, null,
                    UserRole.CUSTOMER, null, UserStatus.ACTIVE, false,
                    LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")), 0,
                    null, null, null, profile.sub()));
            audit.record(created.id(), "USER_REGISTERED", "USER", created.id(), "GOOGLE");
            return created;
        });
        if (!user.role().canUseGoogle()) {
            throw new IdentityException(403, "GOOGLE_ROLE_FORBIDDEN", "Tài khoản này không thể đăng nhập bằng Google");
        }
        if (!user.status().isActive()) {
            throw new IdentityException(403, "ACCOUNT_LOCKED", "Tài khoản đã bị khóa");
        }
        return new AuthResult(user, tokens.issue(user.id(), user.tokenVersion()));
    }

    public UserAccount linkGoogle(Long userId, String password, String idToken) {
        IGoogleIdentityPort.GoogleProfile profile = google.verify(idToken);
        return unitOfWork.execute(() -> linkGoogleTransaction(userId, password, profile));
    }

    private UserAccount linkGoogleTransaction(Long userId, String password, IGoogleIdentityPort.GoogleProfile profile) {
        UserAccount user = find(userId);
        if (!user.status().isActive()) {
            throw new IdentityException(403, "ACCOUNT_LOCKED", "Tài khoản đã bị khóa");
        }
        if (!user.role().canUseGoogle()) {
            throw new IdentityException(403, "GOOGLE_ROLE_FORBIDDEN", "Tài khoản này không thể liên kết Google");
        }
        if (user.passwordHash() == null || !passwords.matches(password, user.passwordHash())) {
            throw new IdentityException(401, "INVALID_CREDENTIALS", "Mật khẩu hiện tại không đúng");
        }
        if (!normalize(user.email()).equals(normalize(profile.email()))) {
            throw new IdentityException(409, "GOOGLE_EMAIL_MISMATCH", "Email Google phải trùng email tài khoản hiện tại");
        }
        if (user.googleSub() != null) {
            if (user.googleSub().equals(profile.sub())) return user;
            throw new IdentityException(409, "GOOGLE_ALREADY_LINKED", "Tài khoản đã liên kết với Google khác");
        }
        if (users.findByGoogleSub(profile.sub()).isPresent()) {
            throw new IdentityException(409, "GOOGLE_ACCOUNT_IN_USE", "Tài khoản Google đã được liên kết nơi khác");
        }
        UserAccount linked = users.save(new UserAccount(user.id(), user.email(), user.passwordHash(),
                user.fullName(), user.phone(), user.role(), user.storeId(), user.status(),
                user.mustChangePassword(), user.createdAt(), user.tokenVersion(),
                user.address(), user.latitude(), user.longitude(), profile.sub()));
        audit.record(user.id(), "GOOGLE_ACCOUNT_LINKED", "USER", user.id(), null);
        return linked;
    }

    public UserAccount find(Long id) {
        return users.findById(id).orElseThrow(() -> new IdentityException(404, "NOT_FOUND", "Không tìm thấy người dùng"));
    }

    public UserCounts countUsers() {
        return new UserCounts(users.countActiveCustomers(), users.countAll());
    }

    public com.foodrescue.identity.domain.model.PageResult<UserAccount> listStaff(
            Long actorId, Long storeId, int page, int size) {
        UserAccount actor = find(actorId);
        if (actor.role() != UserRole.STORE_OWNER || !actor.status().isActive()) {
            throw new IdentityException(403, "FORBIDDEN", "Chỉ chủ cửa hàng được xem nhân viên");
        }
        if (page < 0 || size < 1 || size > 100) {
            throw new IdentityException(400, "INVALID_PAGINATION", "Phân trang không hợp lệ");
        }
        return users.findStaffByStoreId(storeId, page, size);
    }

    public StaffResult createStaff(Long actorId, Long storeId, String email, String fullName, String phone) {
        return unitOfWork.execute(() -> createStaffTransaction(actorId, storeId, email, fullName, phone));
    }

    private StaffResult createStaffTransaction(Long actorId, Long storeId, String email,
                                               String fullName, String phone) {
        UserAccount actor = find(actorId);
        if (actor.role() != UserRole.STORE_OWNER || !actor.status().isActive()) {
            throw new IdentityException(403, "FORBIDDEN", "Chỉ chủ cửa hàng đang hoạt động được tạo nhân viên");
        }
        String normalized = normalize(email);
        if (users.findByEmail(normalized).isPresent()) {
            throw new IdentityException(409, "EMAIL_EXISTS", "Email đã được sử dụng");
        }
        String temporaryPassword = randomPassword();
        UserAccount saved = users.save(new UserAccount(null, normalized, passwords.hash(temporaryPassword),
                fullName.trim(), phone.trim(), UserRole.STORE_STAFF, storeId, UserStatus.ACTIVE, true,
                LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")), 0));
        audit.record(actorId, "STORE_STAFF_CREATED", "USER", saved.id(), "storeId=" + storeId);
        return new StaffResult(saved, temporaryPassword);
    }

    public UserAccount setStaffLocked(Long actorId, Long storeId, Long staffId, boolean locked) {
        return unitOfWork.execute(() -> setStaffLockedTransaction(actorId, storeId, staffId, locked));
    }

    private UserAccount setStaffLockedTransaction(Long actorId, Long storeId, Long staffId, boolean locked) {
        UserAccount actor = find(actorId);
        if (actor.role() != UserRole.STORE_OWNER || !actor.status().isActive()) {
            throw new IdentityException(403, "FORBIDDEN", "Chỉ chủ cửa hàng đang hoạt động được quản lý nhân viên");
        }
        UserAccount staff = find(staffId);
        if (staff.role() != UserRole.STORE_STAFF || !storeId.equals(staff.storeId())) {
            throw new IdentityException(404, "STAFF_NOT_FOUND", "Không tìm thấy nhân viên của cửa hàng");
        }
        UserStatus nextStatus = UserStatus.fromLocked(locked);
        if (nextStatus == staff.status()) return staff;
        UserAccount saved = users.save(copy(staff, staff.passwordHash(), staff.fullName(), staff.phone(),
                nextStatus, staff.mustChangePassword(), staff.tokenVersion() + 1));
        audit.record(actorId, locked ? "STORE_STAFF_LOCKED" : "STORE_STAFF_UNLOCKED", "USER", staffId,
                "storeId=" + storeId);
        return saved;
    }

    public UserAccount updateProfile(Long id, String fullName, String phone,
                                     String address, BigDecimal latitude, BigDecimal longitude) {
        return unitOfWork.execute(() -> updateProfileTransaction(id, fullName, phone, address, latitude, longitude));
    }

    private UserAccount updateProfileTransaction(Long id, String fullName, String phone,
                                                  String address, BigDecimal latitude, BigDecimal longitude) {
        UserAccount old = find(id);
        if ((latitude == null) != (longitude == null)) {
            throw new IdentityException(400, "INVALID_LOCATION", "Cần nhập cả vĩ độ và kinh độ");
        }
        if (latitude != null && (latitude.compareTo(BigDecimal.valueOf(-90)) < 0 || latitude.compareTo(BigDecimal.valueOf(90)) > 0)
                || longitude != null && (longitude.compareTo(BigDecimal.valueOf(-180)) < 0 || longitude.compareTo(BigDecimal.valueOf(180)) > 0)) {
            throw new IdentityException(400, "INVALID_LOCATION", "Tọa độ không hợp lệ");
        }
        UserAccount saved = users.save(new UserAccount(old.id(), old.email(), old.passwordHash(),
                fullName.trim(), phone.trim(), old.role(), old.storeId(), old.status(),
                old.mustChangePassword(), old.createdAt(), old.tokenVersion(),
                address == null || address.isBlank() ? null : address.trim(), latitude, longitude, old.googleSub()));
        audit.record(id, "USER_PROFILE_UPDATED", "USER", id, null);
        return saved;
    }

    public void changePassword(Long id, String oldPassword, String newPassword) {
        unitOfWork.execute(() -> { changePasswordTransaction(id, oldPassword, newPassword); return null; });
    }

    private void changePasswordTransaction(Long id, String oldPassword, String newPassword) {
        UserAccount old = find(id);
        if (old.passwordHash() == null) {
            throw new IdentityException(409, "PASSWORD_NOT_SET", "Tài khoản Google chưa có mật khẩu");
        }
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
        UserRole parsedRole = parseEnumFilter(() -> UserRole.fromNullable(role),
                "Vai trò người dùng không hợp lệ: " + role);
        UserStatus parsedStatus = parseEnumFilter(() -> UserStatus.fromNullable(status),
                "Trạng thái tài khoản không hợp lệ: " + status);
        return users.search(parsedRole, parsedStatus, keyword, page, size);
    }

    private static <E extends Enum<E>> E parseEnumFilter(
            java.util.function.Supplier<java.util.Optional<E>> parser,
            String errorMessage) {
        try {
            return parser.get().orElse(null);
        } catch (IllegalArgumentException ex) {
            throw new IdentityException(400, "VALIDATION_ERROR", errorMessage);
        }
    }

    public UserAccount setLocked(Long actorId, Long targetId, boolean locked) {
        return unitOfWork.execute(() -> setLockedTransaction(actorId, targetId, locked));
    }

    private UserAccount setLockedTransaction(Long actorId, Long targetId, boolean locked) {
        UserAccount old = find(targetId);
        if (locked && (targetId.equals(actorId) || old.role() == UserRole.ADMIN)) {
            throw new IdentityException(403, "FORBIDDEN", "Không thể khóa tài khoản quản trị");
        }
        UserStatus nextStatus = UserStatus.fromLocked(locked);
        if (nextStatus == old.status()) return old;
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

    public PageResult<Map<String, Object>> auditLogs(Long actorId, String action, String entityType,
            Long entityId, String from, String to, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IdentityException(400, "VALIDATION_ERROR", "page hoặc size không hợp lệ");
        }
        if (actorId != null && actorId <= 0 || entityId != null && entityId <= 0) {
            throw new IdentityException(400, "VALIDATION_ERROR", "actorId hoặc entityId không hợp lệ");
        }
        String normalizedAction = normalizeAuditFilter(action, 40);
        String normalizedEntityType = normalizeAuditFilter(entityType, 20);
        LocalDateTime fromTime = parseAuditTime(from);
        LocalDateTime toTime = parseAuditTime(to);
        if (fromTime != null && toTime != null && fromTime.isAfter(toTime)) {
            throw new IdentityException(400, "VALIDATION_ERROR", "from phải trước hoặc bằng to");
        }
        return audit.search(actorId, normalizedAction, normalizedEntityType, entityId, fromTime, toTime, page, size);
    }

    private String normalizeAuditFilter(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > maxLength) {
            throw new IdentityException(400, "VALIDATION_ERROR", "Bộ lọc audit quá dài");
        }
        return normalized;
    }

    private LocalDateTime parseAuditTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDateTime.ofInstant(OffsetDateTime.parse(value).toInstant(), ZoneId.of("Asia/Ho_Chi_Minh"));
        } catch (DateTimeParseException ex) {
            throw new IdentityException(400, "VALIDATION_ERROR", "from/to phải là thời gian ISO-8601 có múi giờ");
        }
    }

    private UserAccount copy(UserAccount old, String passwordHash, String fullName, String phone,
                             UserStatus status, boolean mustChangePassword, long version) {
        return new UserAccount(old.id(), old.email(), passwordHash, fullName, phone, old.role(), old.storeId(),
                status, mustChangePassword, old.createdAt(), version,
                old.address(), old.latitude(), old.longitude(), old.googleSub());
    }

    private String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }

    private String randomPassword() {
        StringBuilder result = new StringBuilder(16);
        for (int i = 0; i < 16; i++) result.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return result.toString();
    }

    public record AuthResult(UserAccount user, String token) {}
    public record ResetResult(Long userId, String temporaryPassword, boolean mustChangePassword) {}
    public record UserCounts(long activeCustomers, long totalUsers) {}
    public record StaffResult(UserAccount user, String temporaryPassword) {}
}
