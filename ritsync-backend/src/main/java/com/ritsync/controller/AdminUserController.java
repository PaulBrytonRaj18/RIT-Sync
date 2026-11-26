package com.ritsync.controller;

import com.ritsync.dto.request.CreateUserRequest;
import com.ritsync.dto.response.ApiResponse;
import com.ritsync.dto.response.UserResponse;
import com.ritsync.security.UserPrincipal;
import com.ritsync.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> createUser(@Valid @RequestBody CreateUserRequest req,
                                                @AuthenticationPrincipal UserPrincipal admin) {
        return ApiResponse.ok("User created", adminUserService.createUser(req, admin));
    }

    /** Lets a student enroll their face again (e.g. appearance changed, or a wrong face was enrolled). */
    @DeleteMapping("/{userId}/face")
    public ApiResponse<Void> resetFace(@PathVariable Long userId,
                                       @AuthenticationPrincipal UserPrincipal admin) {
        adminUserService.resetFace(userId, admin);
        return ApiResponse.ok("Face ID reset", null);
    }

    /** Removes all device bindings; the user's next sign-in device becomes trusted. */
    @DeleteMapping("/{userId}/devices")
    public ApiResponse<Void> resetDevices(@PathVariable Long userId,
                                          @AuthenticationPrincipal UserPrincipal admin) {
        adminUserService.resetDevices(userId, admin);
        return ApiResponse.ok("Devices reset", null);
    }
}
