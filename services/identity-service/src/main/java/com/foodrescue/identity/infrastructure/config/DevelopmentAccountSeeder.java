package com.foodrescue.identity.infrastructure.config;

import com.foodrescue.identity.application.port.out.IPasswordPort;
import com.foodrescue.identity.domain.model.UserAccount;
import com.foodrescue.identity.domain.model.enums.UserRole;
import com.foodrescue.identity.domain.model.enums.UserStatus;
import com.foodrescue.identity.domain.repository.IUserRepository;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class DevelopmentAccountSeeder implements CommandLineRunner {
    private final IUserRepository users;
    private final IPasswordPort passwords;
    private final TransactionTemplate transaction;

    @Value("${app.seed.enabled:false}") private boolean enabled;
    @Value("${app.seed.admin.email:}") private String adminEmail;
    @Value("${app.seed.admin.password:}") private String adminPassword;
    @Value("${app.seed.owner.email:}") private String ownerEmail;
    @Value("${app.seed.owner.password:}") private String ownerPassword;

    public DevelopmentAccountSeeder(IUserRepository users, IPasswordPort passwords,
                                    TransactionTemplate transaction) {
        this.users = users;
        this.passwords = passwords;
        this.transaction = transaction;
    }

    @Override
    public void run(String... args) {
        if (!enabled) return;
        transaction.executeWithoutResult(status -> {
            seed(adminEmail, adminPassword, UserRole.ADMIN, null, "Quản trị viên mẫu");
            seed(ownerEmail, ownerPassword, UserRole.STORE_OWNER, null, "Chủ cửa hàng mẫu");
        });
    }

    private void seed(String rawEmail, String password, UserRole role, Long storeId, String name) {
        if (rawEmail == null || rawEmail.isBlank() || password == null || password.length() < 8) {
            throw new IllegalStateException("Seed email/password missing or password shorter than 8 characters");
        }
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        var existing = users.findByEmail(email);
        if (existing.isPresent()) {
            if (role != existing.get().role()) {
                throw new IllegalStateException("Seed email already belongs to a different role: " + email);
            }
            return; // Never reset a password or overwrite a real account on restart.
        }
        users.save(new UserAccount(null, email, passwords.hash(password), name, "", role,
                storeId, UserStatus.ACTIVE, false, LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh")), 0));
    }
}
