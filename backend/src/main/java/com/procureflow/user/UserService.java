package com.procureflow.user;

import com.procureflow.audit.AuditService;
import com.procureflow.common.ApiException;
import com.procureflow.common.PageResponse;
import com.procureflow.common.Paging;
import com.procureflow.security.SecurityUtils;
import com.procureflow.user.UserDtos.ChangePasswordRequest;
import com.procureflow.user.UserDtos.CreateUserRequest;
import com.procureflow.user.UserDtos.UpdateProfileRequest;
import com.procureflow.user.UserDtos.UpdateUserRequest;
import com.procureflow.user.UserDtos.UserDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public UserService(UserRepository users, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
    }

    // ---- self service ----------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public UserDto me() {
        return UserDto.from(currentUser());
    }

    @Transactional
    public UserDto updateProfile(UpdateProfileRequest in) {
        User user = currentUser();
        user.setFullName(in.fullName().trim());
        user.setDepartment(blankToNull(in.department()));
        audit.log("UPDATE_PROFILE", "USER", user.getId(), "Updated own profile");
        return UserDto.from(user);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest in) {
        User user = currentUser();
        if (!encoder.matches(in.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Current password is incorrect");
        }
        if (encoder.matches(in.newPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("New password must be different from the current password");
        }
        user.setPasswordHash(encoder.encode(in.newPassword()));
        audit.log("CHANGE_PASSWORD", "USER", user.getId(), "Changed own password");
    }

    // ---- administration --------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<UserDto> list(String search, Role role, Boolean active, int page, int size) {
        Specification<User> spec = (root, query, cb) -> cb.conjunction();
        if (StringUtils.hasText(search)) {
            String like = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("email")), like),
                    cb.like(cb.lower(root.get("fullName")), like),
                    cb.like(cb.lower(root.get("department")), like)));
        }
        if (role != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("role"), role));
        }
        if (active != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("active"), active));
        }
        Page<User> result = users.findAll(spec, Paging.of(page, size, Sort.by(Sort.Direction.ASC, "fullName", "id")));
        return PageResponse.of(result, UserDto::from);
    }

    @Transactional(readOnly = true)
    public UserDto get(Long id) {
        return UserDto.from(load(id));
    }

    @Transactional
    public UserDto create(CreateUserRequest in) {
        String email = in.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("A user with email " + email + " already exists");
        }
        User user = new User();
        user.setEmail(email);
        user.setFullName(in.fullName().trim());
        user.setDepartment(blankToNull(in.department()));
        user.setRole(in.role());
        user.setPasswordHash(encoder.encode(in.password()));
        user.setActive(true);
        user = users.save(user);
        audit.log("CREATE", "USER", user.getId(), "Created user " + email + " with role " + user.getRole());
        return UserDto.from(user);
    }

    @Transactional
    public UserDto update(Long id, UpdateUserRequest in) {
        User user = load(id);
        Long currentId = SecurityUtils.current().id();
        if (user.getId().equals(currentId) && in.role() != user.getRole()) {
            throw ApiException.badRequest("You cannot change your own role");
        }
        Role oldRole = user.getRole();
        user.setFullName(in.fullName().trim());
        user.setDepartment(blankToNull(in.department()));
        user.setRole(in.role());
        audit.log("UPDATE", "USER", user.getId(),
                "Updated user " + user.getEmail() + (oldRole != in.role() ? " (role " + oldRole + " -> " + in.role() + ")" : ""));
        return UserDto.from(user);
    }

    @Transactional
    public UserDto setActive(Long id, boolean active) {
        User user = load(id);
        if (!active && user.getId().equals(SecurityUtils.current().id())) {
            throw ApiException.badRequest("You cannot deactivate your own account");
        }
        user.setActive(active);
        audit.log(active ? "ACTIVATE" : "DEACTIVATE", "USER", user.getId(),
                (active ? "Activated " : "Deactivated ") + user.getEmail());
        return UserDto.from(user);
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private User currentUser() {
        Long id = SecurityUtils.current().id();
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User", id));
    }

    private User load(Long id) {
        return users.findById(id).orElseThrow(() -> ApiException.notFound("User", id));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
