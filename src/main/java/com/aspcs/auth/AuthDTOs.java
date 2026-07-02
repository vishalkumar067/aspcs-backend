package com.aspcs.auth;

// ─── DTOs ────────────────────────────────────────────────────────────────────

class LoginRequest {
    public String email;
    public String password;
}

class LoginResponse {
    public UserDTO user;
    public TokenDTO tokens;

    public LoginResponse(UserDTO user, TokenDTO tokens) {
        this.user   = user;
        this.tokens = tokens;
    }
}

class UserDTO {
    public String id;
    public String name;
    public String email;
    public String role;
    public String teacherId;

    public UserDTO(com.aspcs.auth.entity.AdminUser u) {
        this.id    = u.getId().toString();
        this.name  = u.getName();
        this.email = u.getEmail();
        this.role  = u.getRole().name();
        this.teacherId = u.getTeacherId() != null ? u.getTeacherId().toString() : null;
    }
}

class TokenDTO {
    public String accessToken;
    public String refreshToken;
    public long   expiresIn = 86400000L;

    public TokenDTO(String accessToken, String refreshToken) {
        this.accessToken  = accessToken;
        this.refreshToken = refreshToken;
    }
}

class RefreshRequest {
    public String refreshToken;
}

class ChangePasswordRequest {
    public String currentPassword;
    public String newPassword;
}

// ─── User management DTOs (admin-only) ──────────────────────────

class CreateUserRequest {
    public String name;
    public String email;
    public String password;
    public String role; // SUPER_ADMIN, ADMIN, EDITOR, TEACHER, STUDENT
}

class UpdateUserRequest {
    public String name;
    public String email;
    public String role;
}

class ResetPasswordRequest {
    public String newPassword;
}

class UserListDTO {
    public String id;
    public String name;
    public String email;
    public String role;
    public String teacherId;
    public String createdAt;

    public UserListDTO(com.aspcs.auth.entity.AdminUser u) {
        this.id    = u.getId().toString();
        this.name  = u.getName();
        this.email = u.getEmail();
        this.role  = u.getRole().name();
        this.teacherId = u.getTeacherId() != null ? u.getTeacherId().toString() : null;
        this.createdAt = u.getCreatedAt() != null ? u.getCreatedAt().toString() : null;
    }
}
