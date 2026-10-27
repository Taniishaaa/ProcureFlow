package com.procureflow.user;

import com.procureflow.common.PageResponse;
import com.procureflow.user.UserDtos.ChangePasswordRequest;
import com.procureflow.user.UserDtos.CreateUserRequest;
import com.procureflow.user.UserDtos.SetActiveRequest;
import com.procureflow.user.UserDtos.UpdateProfileRequest;
import com.procureflow.user.UserDtos.UpdateUserRequest;
import com.procureflow.user.UserDtos.UserDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    // ---- current user ----------------------------------------------------------------------------------------

    @GetMapping("/me")
    public UserDto me() {
        return service.me();
    }

    @PutMapping("/me")
    public UserDto updateProfile(@Valid @RequestBody UpdateProfileRequest body) {
        return service.updateProfile(body);
    }

    @PostMapping("/me/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest body) {
        service.changePassword(body);
    }

    // ---- administration (ADMIN only) -------------------------------------------------------------------------

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<UserDto> list(@RequestParam(required = false) String search,
                                      @RequestParam(required = false) Role role,
                                      @RequestParam(required = false) Boolean active,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "10") int size) {
        return service.list(search, role, active, page, size);
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto create(@Valid @RequestBody CreateUserRequest body) {
        return service.create(body);
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest body) {
        return service.update(id, body);
    }

    @PatchMapping("/users/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public UserDto setActive(@PathVariable Long id, @RequestBody SetActiveRequest body) {
        return service.setActive(id, body.active());
    }
}
