package com.procureflow.security;

import com.procureflow.user.Role;

/** The authenticated caller, available through {@link SecurityUtils#current()}. */
public record UserPrincipal(Long id, String email, String fullName, Role role) {
}
