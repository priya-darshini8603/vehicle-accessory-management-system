package org.example.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.User;
import org.example.enums.Role;
import org.example.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

// Public registration only ever creates CUSTOMER accounts. The first administrator is provisioned
// here from app.admin.* (ADMIN_EMAIL / ADMIN_PASSWORD); further admins are promoted via
// PUT /api/admin/users/{id}/role.
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private static final int MIN_PASSWORD_LENGTH = 12;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:}")
    private String email;

    @Value("${app.admin.password:}")
    private String password;

    @Value("${app.admin.full-name:System Administrator}")
    private String fullName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email == null || email.isBlank()) {
            log.info("app.admin.email not set; skipping admin bootstrap");
            return;
        }
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        userRepository.findByEmail(normalizedEmail).ifPresentOrElse(existing -> {
            // Never overwrite the password of an existing account; only ensure it has the ADMIN role.
            if (existing.getRole() != Role.ADMIN) {
                existing.setRole(Role.ADMIN);
                log.info("Promoted existing user {} to ADMIN", normalizedEmail);
            }
        }, () -> {
            if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
                throw new IllegalStateException("app.admin.password must be set and contain at least "
                        + MIN_PASSWORD_LENGTH + " characters when app.admin.email is configured");
            }
            User admin = new User();
            admin.setEmail(normalizedEmail);
            admin.setFullName(fullName);
            admin.setPassword(passwordEncoder.encode(password));
            admin.setRole(Role.ADMIN);
            userRepository.save(admin);
            log.info("Created bootstrap admin account {}", normalizedEmail);
        });
    }
}
