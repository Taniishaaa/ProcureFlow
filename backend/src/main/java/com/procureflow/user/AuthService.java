package com.procureflow.user;

import com.procureflow.audit.AuditService;
import com.procureflow.common.ApiException;
import com.procureflow.security.JwtService;
import com.procureflow.user.UserDtos.ForgotPasswordRequest;
import com.procureflow.user.UserDtos.ForgotPasswordResponse;
import com.procureflow.user.UserDtos.LoginRequest;
import com.procureflow.user.UserDtos.LoginResponse;
import com.procureflow.user.UserDtos.ResetPasswordRequest;
import com.procureflow.user.UserDtos.UserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Duration RESET_TOKEN_LIFETIME = Duration.ofMinutes(30);
    private static final String GENERIC_RESET_MESSAGE =
            "If an active account exists for that email, a password reset link has been generated.";

    private final UserRepository users;
    private final PasswordResetTokenRepository resetTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;
    private final AuditService audit;
    private final boolean exposeResetToken;

    public AuthService(UserRepository users, PasswordResetTokenRepository resetTokens, PasswordEncoder encoder,
                       JwtService jwtService, AuditService audit,
                       @Value("${app.dev-expose-reset-token:false}") boolean exposeResetToken) {
        this.users = users;
        this.resetTokens = resetTokens;
        this.encoder = encoder;
        this.jwtService = jwtService;
        this.audit = audit;
        this.exposeResetToken = exposeResetToken;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(AuthService::invalidCredentials);
        if (!encoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        if (!user.isActive()) {
            throw ApiException.forbidden("Your account has been deactivated. Please contact an administrator.");
        }
        user.setLastLoginAt(Instant.now());
        audit.logAs(user.getId(), user.getEmail(), "LOGIN", "USER", user.getId(), null);
        return new LoginResponse(jwtService.generate(user), "Bearer", jwtService.lifetimeSeconds(),
                UserDto.from(user));
    }

    @Transactional
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
        String token = null;
        User user = users.findByEmailIgnoreCase(request.email().trim()).orElse(null);
        if (user != null && user.isActive()) {
            PasswordResetToken entity = new PasswordResetToken();
            entity.setUser(user);
            entity.setToken(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
            entity.setExpiresAt(Instant.now().plus(RESET_TOKEN_LIFETIME));
            resetTokens.save(entity);
            token = entity.getToken();
            audit.logAs(user.getId(), user.getEmail(), "PASSWORD_RESET_REQUESTED", "USER", user.getId(), null);
            if (exposeResetToken) {
                log.info("DEV ONLY - password reset for {}: /reset-password?token={}", user.getEmail(), token);
            }
        }
        // The response is identical whether or not the account exists, to avoid revealing registered emails.
        return new ForgotPasswordResponse(GENERIC_RESET_MESSAGE, exposeResetToken ? token : null);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = resetTokens.findByToken(request.token().trim())
                .orElseThrow(() -> ApiException.badRequest("This reset link is invalid or has expired"));
        if (token.isUsed() || token.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("This reset link is invalid or has expired");
        }
        User user = token.getUser();
        user.setPasswordHash(encoder.encode(request.newPassword()));
        token.setUsed(true);
        audit.logAs(user.getId(), user.getEmail(), "PASSWORD_RESET", "USER", user.getId(), null);
    }

    private static ApiException invalidCredentials() {
        return ApiException.unauthorized("Invalid email or password");
    }
}
