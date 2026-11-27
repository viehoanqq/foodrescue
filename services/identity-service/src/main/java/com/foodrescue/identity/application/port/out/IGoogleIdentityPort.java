package com.foodrescue.identity.application.port.out;

public interface IGoogleIdentityPort {
    GoogleProfile verify(String idToken);

    record GoogleProfile(String sub, String email, String fullName) {}
}
