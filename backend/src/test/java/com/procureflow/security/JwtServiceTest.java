package com.procureflow.security;

import com.procureflow.user.Role;
import com.procureflow.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-that-is-definitely-longer-than-32-chars";

    private User user() {
        User user = new User();
        user.setId(42L);
        user.setEmail("someone@example.com");
        user.setRole(Role.MANAGER);
        return user;
    }

    @Test
    void generatedTokenCanBeParsed() {
        JwtService service = new JwtService(SECRET, 60);
        Claims claims = service.parse(service.generate(user()));
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("role", String.class)).isEqualTo("MANAGER");
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtService issuer = new JwtService(SECRET, 60);
        JwtService other = new JwtService("a-completely-different-secret-with-enough-length-1234", 60);
        String token = issuer.generate(user());
        assertThatThrownBy(() -> other.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService service = new JwtService(SECRET, -1);
        String token = service.generate(user());
        assertThatThrownBy(() -> service.parse(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void shortSecretIsRefusedAtStartup() {
        assertThatThrownBy(() -> new JwtService("too-short", 60)).isInstanceOf(IllegalStateException.class);
    }
}
