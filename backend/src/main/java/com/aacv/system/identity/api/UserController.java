package com.aacv.system.identity.api;

import com.aacv.system.identity.application.AdminUserService;
import com.aacv.system.identity.application.CreateUserCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final AdminUserService adminUserService;

    public UserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    public UserPageResponse findPage(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return UserPageResponse.from(adminUserService.findPage(page, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return UserResponse.from(adminUserService.createUser(
                new CreateUserCommand(request.username(), request.password(), request.roles())));
    }

    @PostMapping("/{userId}/enable")
    public UserResponse enable(@PathVariable long userId, @Valid @RequestBody VersionRequest request) {
        return UserResponse.from(adminUserService.enableUser(userId, request.version()));
    }

    @PostMapping("/{userId}/disable")
    public UserResponse disable(@PathVariable long userId, @Valid @RequestBody VersionRequest request) {
        return UserResponse.from(adminUserService.disableUser(userId, request.version()));
    }

    @PostMapping("/{userId}/reset-password")
    public UserResponse resetPassword(
            @PathVariable long userId, @Valid @RequestBody ResetPasswordRequest request) {
        return UserResponse.from(
                adminUserService.resetPassword(userId, request.version(), request.newPassword()));
    }

    @PostMapping("/{userId}/roles")
    public UserResponse replaceRoles(
            @PathVariable long userId, @Valid @RequestBody ReplaceRolesRequest request) {
        return UserResponse.from(
                adminUserService.replaceRoles(userId, request.version(), request.roles()));
    }
}
