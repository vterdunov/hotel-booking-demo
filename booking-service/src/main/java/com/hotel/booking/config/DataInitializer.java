package com.hotel.booking.config;

import com.hotel.booking.entity.Role;
import com.hotel.booking.entity.User;
import com.hotel.booking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(name = "app.admin.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties adminProperties;

    @Override
    public void run(String... args) {
        if (!StringUtils.hasText(adminProperties.password())) {
            log.warn("Admin password is not set. Skipping admin user creation.");
            return;
        }

        String username = StringUtils.hasText(adminProperties.username())
                ? adminProperties.username()
                : "admin";

        if (!userRepository.existsByUsername(username)) {
            User admin = User.builder()
                    .username(username)
                    .password(passwordEncoder.encode(adminProperties.password()))
                    .role(Role.ADMIN)
                    .build();
            userRepository.save(admin);
            log.info("Default admin user '{}' created", username);
        } else {
            log.debug("Admin user '{}' already exists", username);
        }
    }
}
