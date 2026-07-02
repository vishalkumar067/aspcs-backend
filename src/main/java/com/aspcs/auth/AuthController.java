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

    // ─── User Management (SUPER_ADMIN / ADMIN only) ────────────────────────

    @GetMapping("/users")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<java.util.List<UserListDTO>>> listUsers() {
        return ResponseEntity.ok(ApiResponse.ok(authService.listAllUsers()));
    }

    @PostMapping("/users")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserDTO>> createUser(@RequestBody CreateUserRequest request) {
        var user = authService.createUser(request);
        return ResponseEntity.ok(ApiResponse.ok(new UserDTO(user), "User created successfully"));
    }

    @PutMapping("/users/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<UserDTO>> updateUser(
            @PathVariable java.util.UUID id, @RequestBody UpdateUserRequest request) {
        var user = authService.updateUser(id, request);
        return ResponseEntity.ok(ApiResponse.ok(new UserDTO(user), "User updated successfully"));
    }

    @PostMapping("/users/{id}/reset-password")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable java.util.UUID id, @RequestBody ResetPasswordRequest request) {
        authService.resetUserPassword(id, request);
        return ResponseEntity.ok(ApiResponse.ok(null, "Password reset successfully"));
    }

    @DeleteMapping("/users/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUser(
            @PathVariable java.util.UUID id, @AuthenticationPrincipal AdminUser admin) {
        authService.deleteUser(id, admin);
        return ResponseEntity.ok(ApiResponse.ok(null, "User deleted successfully"));
    }
}
