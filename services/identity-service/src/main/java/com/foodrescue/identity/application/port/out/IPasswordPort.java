package com.foodrescue.identity.application.port.out;

public interface IPasswordPort {
    String hash(String raw);
    boolean matches(String raw, String encoded);
}
