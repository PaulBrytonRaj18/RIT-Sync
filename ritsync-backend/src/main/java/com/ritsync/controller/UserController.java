package com.ritsync.controller;

import com.ritsync.dto.response.ApiResponse;
import com.ritsync.dto.response.UserResponse;
import com.ritsync.enums.Role;
import com.ritsync.security.UserPrincipal;
import com.ritsync.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<UserResponse> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.ok(userService.getById(principal.getId()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
    public ApiResponse<UserResponse> getUser(@PathVariable Long id) {
        return ApiResponse.ok(userService.getById(id));
    }

    @GetMapping("/role/{role}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ApiResponse<List<UserResponse>> getUsersByRole(@PathVariable Role role) {
        return ApiResponse.ok(userService.getUsersByRole(role));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> deactivateUser(@PathVariable Long id) {
        userService.deactivateUser(id);
        return ApiResponse.ok("User deactivated", null);
    }
}
