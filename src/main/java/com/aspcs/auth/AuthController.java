package com.aspcs.auth;

import com.aspcs.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import com.aspcs.auth.entity.AdminUser;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Login successful"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenDTO>> refresh(@RequestBody RefreshRequest request) {
        TokenDTO tokens = authService.refresh(request.refreshToken);
        return ResponseEntity.ok(ApiResponse.ok(tokens, "Token refreshed"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDTO>> me(
            @AuthenticationPrincipal AdminUser user) {
        return ResponseEntity.ok(ApiResponse.ok(new UserDTO(user)));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal AdminUser user,
            @RequestBody ChangePasswordRequest request) {
        authService.changePassword(user, request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Password changed successfully"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        // JWT is stateless — client just discards the token
        return ResponseEntity.ok(ApiResponse.ok(null, "Logged out successfully"));
    }
}
