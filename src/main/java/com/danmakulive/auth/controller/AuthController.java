package com.danmakulive.auth.controller;

import com.danmakulive.auth.context.UserHolder;
import com.danmakulive.auth.model.dto.AuthResponse;
import com.danmakulive.auth.model.dto.LoginRequest;
import com.danmakulive.auth.model.dto.RegisterRequest;
import com.danmakulive.auth.model.dto.UserDTO;
import com.danmakulive.auth.service.AuthService;
import com.danmakulive.common.result.Result;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Result<AuthResponse> register(@RequestBody RegisterRequest req) {
        return Result.success(authService.register(req));
    }

    @PostMapping("/login")
    public Result<AuthResponse> login(@RequestBody LoginRequest req) {
        return Result.success(authService.login(req));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "authorization", required = false) String token) {
        authService.logout(token);
        UserHolder.removeUser();
        return Result.success();
    }

    @GetMapping("/me")
    public Result<?> me() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.failure("A000002", "未登录");
        }
        return Result.success(user);
    }
}
