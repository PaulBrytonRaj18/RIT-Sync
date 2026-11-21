package com.ritsync.controller;

import com.ritsync.dto.request.LoginRequest;
import com.ritsync.dto.request.RefreshTokenRequest;
import com.ritsync.dto.request.RegisterRequest;
import com.ritsync.dto.response.ApiResponse;
import com.ritsync.dto.response.AuthResponse;
import com.ritsync.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ApiResponse.ok("Registration successful", authService.register(req));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.ok("Login successful", authService.login(req));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest req) {
        return ApiResponse.ok("Token refreshed", authService.refresh(req.refreshToken()));
    }
}
