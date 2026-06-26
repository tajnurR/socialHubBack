package com.socialhub.socialhubBackend.auth;

import com.socialhub.socialhubBackend.config.AppProperties;
import com.socialhub.socialhubBackend.config.AuthProperties;
import com.socialhub.socialhubBackend.user.domain.User;
import com.socialhub.socialhubBackend.user.domain.UserRole;
import com.socialhub.socialhubBackend.user.domain.UserStatus;
import com.socialhub.socialhubBackend.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds an initial admin user on startup so the first login works. Idempotent:
 * creates the admin (or sets its password if it exists without one). Disable via
 * {@code app.auth.seed-admin-enabled=false} once a real admin exists / in prod.
 */
@Component
public class AuthSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AuthSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;
    private final AppProperties appProperties;

    public AuthSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthProperties authProperties,
            AppProperties appProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authProperties = authProperties;
        this.appProperties = appProperties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!authProperties.seedAdminEnabled()) {
            return;
        }
        String email = authProperties.seedAdminEmail().trim().toLowerCase();
        User admin = userRepository.findByEmail(email).orElseGet(() -> {
            User user = new User();
            user.setOrganizationId(appProperties.tenant().defaultOrganizationId());
            user.setEmail(email);
            user.setDisplayName("Administrator");
            user.setRole(UserRole.OWNER);
            user.setStatus(UserStatus.ACTIVE);
            return user;
        });
        if (admin.getPasswordHash() == null) {
            admin.setPasswordHash(passwordEncoder.encode(authProperties.seedAdminPassword()));
            userRepository.save(admin);
            log.info("Seeded admin user '{}' (set a real password / disable seeding in prod)", email);
        }
    }
}
