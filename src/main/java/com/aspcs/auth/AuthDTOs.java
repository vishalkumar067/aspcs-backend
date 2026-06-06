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

    public UserDTO(com.aspcs.auth.entity.AdminUser u) {
        this.id   = u.getId().toString();
        this.name = u.getName();
        this.email= u.getEmail();
        this.role = u.getRole().name();
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
