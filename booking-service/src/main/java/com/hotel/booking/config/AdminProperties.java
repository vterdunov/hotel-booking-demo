package com.hotel.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(
        boolean enabled,
        String username,
        String password
) {
}
