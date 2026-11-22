package com.foodrescue.identity.infrastructure.security;

import com.foodrescue.identity.application.port.out.PasswordPort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class BcryptPasswordAdapter implements PasswordPort {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);
    @Override public String hash(String raw) { return encoder.encode(raw); }
    @Override public boolean matches(String raw, String encoded) { return encoder.matches(raw, encoded); }
}
