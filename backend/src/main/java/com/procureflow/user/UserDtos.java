package com.procureflow.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class UserDtos {

    public static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).+$";
    public static final String PASSWORD_MESSAGE = "Password must be 8-72 characters and contain a letter and a digit";

    private UserDtos() {
    }

    public record UserDto(Long id, String email, String fullName, String department, Role role, boolean active,
                          Instant lastLoginAt, Instant createdAt) {
        public static UserDto from(User u) {
            return new UserDto(u.getId(), u.getEmail(), u.getFullName(), u.getDepartment(), u.getRole(),
                    u.isActive(), u.getLastLoginAt(), u.getCreatedAt());
        }
    }

    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Size(max = 150) String fullName,
            @Size(max = 100) String department,
            @NotNull Role role,
            @NotBlank @Size(min = 8, max = 72, message = PASSWORD_MESSAGE)
            @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String password) {
    }

    public record UpdateUserRequest(
            @NotBlank @Size(max = 150) String fullName,
            @Size(max = 100) String department,
            @NotNull Role role) {
    }

    public record SetActiveRequest(boolean active) {
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 150) String fullName,
            @Size(max = 100) String department) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 72, message = PASSWORD_MESSAGE)
            @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String newPassword) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record LoginResponse(String token, String tokenType, long expiresInSeconds, UserDto user) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    public record ForgotPasswordResponse(String message, String resetToken) {
    }

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 72, message = PASSWORD_MESSAGE)
            @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE) String newPassword) {
    }
}
