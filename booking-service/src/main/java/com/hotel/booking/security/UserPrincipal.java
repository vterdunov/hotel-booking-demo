package com.hotel.booking.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class UserPrincipal {
    private Long id;
    private String username;
    private String role;
}
