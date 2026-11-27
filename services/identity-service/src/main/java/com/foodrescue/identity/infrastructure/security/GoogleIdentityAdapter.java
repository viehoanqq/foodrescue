package com.foodrescue.identity.infrastructure.security;

import com.foodrescue.identity.application.port.out.IGoogleIdentityPort;
import com.foodrescue.identity.domain.exception.IdentityException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GoogleIdentityAdapter implements IGoogleIdentityPort {
    private final GoogleIdTokenVerifier verifier;

    public GoogleIdentityAdapter(@Value("${app.google.client-id:}") String clientId)
            throws GeneralSecurityException, IOException {
        if (clientId.isBlank()) {
            verifier = null;
        } else {
            verifier = new GoogleIdTokenVerifier.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(List.of(clientId)).build();
        }
    }

    @Override
    public GoogleProfile verify(String idToken) {
        if (verifier == null) {
            throw new IdentityException(503, "GOOGLE_NOT_CONFIGURED", "Chưa cấu hình Google Client ID");
        }
        try {
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null || token.getPayload().getSubject() == null
                    || token.getPayload().getSubject().isBlank()
                    || !Boolean.TRUE.equals(token.getPayload().getEmailVerified())
                    || token.getPayload().getEmail() == null || token.getPayload().getEmail().isBlank()) {
                throw new IdentityException(401, "INVALID_GOOGLE_TOKEN", "Google ID token không hợp lệ");
            }
            GoogleIdToken.Payload payload = token.getPayload();
            String name = payload.get("name") instanceof String value ? value : payload.getEmail();
            return new GoogleProfile(payload.getSubject(), payload.getEmail(), name);
        } catch (IOException ex) {
            throw new IdentityException(503, "GOOGLE_UNAVAILABLE", "Không thể xác minh Google ID token lúc này");
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IdentityException(401, "INVALID_GOOGLE_TOKEN", "Google ID token không hợp lệ");
        }
    }
}
