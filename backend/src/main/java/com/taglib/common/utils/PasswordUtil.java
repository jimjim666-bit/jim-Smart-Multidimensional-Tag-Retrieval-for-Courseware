package com.taglib.common.utils;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordUtil {
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    public static String encode(String raw) {
        return ENCODER.encode(raw);
    }

    public static boolean matches(String raw, String hash) {
        return ENCODER.matches(raw, hash);
    }
}
