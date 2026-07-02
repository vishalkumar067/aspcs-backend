package com.aspcs.auth;

import com.aspcs.auth.entity.AdminUser;
import com.aspcs.security.JwtService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AuthService implements UserDetailsService {

    private final AdminUserRepository  adminUserRepository;
    private final JwtService           jwtService;
    private final PasswordEncoder      passwordEncoder;
    private final ApplicationContext   applicationContext;

    public AuthService(AdminUserRepository adminUserRepository,
                       JwtService jwtService,
                       PasswordEncoder passwordEncoder,
                       ApplicationContext applicationContext) {
        this.adminUserRepository = adminUserRepository;
        this.jwtService          = jwtService;
        this.passwordEncoder     = passwordEncoder;
        this.applicationContext  = applicationContext;
    }

    private AuthenticationManager getAuthManager() {
        return applicationContext.getBean(AuthenticationManager.class);
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return adminUserRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }

    public LoginResponse login(LoginRequest request) {
        getAuthManager().authenticate(
                new UsernamePasswordAuthenticationToken(request.email, request.password)
        );
        AdminUser user = adminUserRepository.findByEmail(request.email).orElseThrow();
        String accessToken  = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        return new LoginResponse(new UserDTO(user), new TokenDTO(accessToken, refreshToken));
    }

    public TokenDTO refresh(String refreshToken) {
        String email = jwtService.extractUsername(refreshToken);
        AdminUser user = adminUserRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        if (!jwtService.isTokenValid(refreshToken, user)) {
            throw new IllegalArgumentException("Refresh token expired or invalid");
        }
        return new TokenDTO(jwtService.generateAccessToken(user), refreshToken);
    }

    // Requires the current password, not just the new one — otherwise
    // anyone who steals a still-valid access token (e.g. from a shared
    // computer, or an XSS-stolen token) could lock the real owner out by
    // changing their password without ever knowing it. Re-checking the
    // current password closes that gap at the cost of one extra prompt.
    public void changePassword(AdminUser currentUser, ChangePasswordRequest request) {
        if (request.newPassword == null || request.newPassword.length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters");
        }
        if (!passwordEncoder.matches(request.currentPassword, currentUser.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword, currentUser.getPassword())) {
            throw new IllegalArgumentException("New password must be different from the current password");
        }
        currentUser.setPassword(passwordEncoder.encode(request.newPassword));
        adminUserRepository.save(currentUser);
        log.info("Password changed for user {}", currentUser.getEmail());
    }

    // ─── User Management (admin-only) ─────────────────────────────────────

    public java.util.List<UserListDTO> listAllUsers() {
        return adminUserRepository.findAll(
                org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(UserListDTO::new)
                .toList();
    }

    public AdminUser createUser(CreateUserRequest request) {
        if (request.name == null || request.name.isBlank())
            throw new IllegalArgumentException("Name is required");
        if (request.email == null || request.email.isBlank())
            throw new IllegalArgumentException("Email is required");
        if (request.password == null || request.password.length() < 8)
            throw new IllegalArgumentException("Password must be at least 8 characters");
        if (request.role == null || request.role.isBlank())
            throw new IllegalArgumentException("Role is required");

        if (adminUserRepository.existsByEmail(request.email)) {
            throw new IllegalArgumentException("A user with this email already exists");
        }

        AdminUser.Role role;
        try {
            role = AdminUser.Role.valueOf(request.role);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid role: " + request.role);
        }

        AdminUser user = AdminUser.builder()
                .name(request.name)
                .email(request.email)
                .password(passwordEncoder.encode(request.password))
                .role(role)
                .build();
        AdminUser saved = adminUserRepository.save(user);
        log.info("User created: {} with role {}", saved.getEmail(), saved.getRole());
        return saved;
    }

    public AdminUser updateUser(java.util.UUID userId, UpdateUserRequest request) {
        AdminUser user = adminUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (request.name != null && !request.name.isBlank()) user.setName(request.name);

        if (request.email != null && !request.email.isBlank() && !request.email.equals(user.getEmail())) {
            if (adminUserRepository.existsByEmail(request.email)) {
                throw new IllegalArgumentException("A user with this email already exists");
            }
            user.setEmail(request.email);
        }

        if (request.role != null && !request.role.isBlank()) {
            try {
                user.setRole(AdminUser.Role.valueOf(request.role));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid role: " + request.role);
            }
        }

        AdminUser saved = adminUserRepository.save(user);
        log.info("User updated: {} → role {}", saved.getEmail(), saved.getRole());
        return saved;
    }

    public void resetUserPassword(java.util.UUID userId, ResetPasswordRequest request) {
        if (request.newPassword == null || request.newPassword.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        AdminUser user = adminUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setPassword(passwordEncoder.encode(request.newPassword));
        adminUserRepository.save(user);
        log.info("Password reset by admin for user {}", user.getEmail());
    }

    public void deleteUser(java.util.UUID userId, AdminUser actingAdmin) {
        if (userId.equals(actingAdmin.getId())) {
            throw new IllegalArgumentException("You cannot delete your own account");
        }
        AdminUser user = adminUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        adminUserRepository.delete(user);
        log.info("User deleted: {} by {}", user.getEmail(), actingAdmin.getEmail());
    }

    @PostConstruct
    public void seedAdmin() {
        if (!adminUserRepository.existsByEmail("admin@aspcs.edu.in")) {
            AdminUser admin = AdminUser.builder()
                    .name("Super Admin")
                    .email("admin@aspcs.edu.in")
                    .password(passwordEncoder.encode("Admin@1234"))
                    .role(AdminUser.Role.SUPER_ADMIN)
                    .build();
            adminUserRepository.save(admin);
            log.info("✅ Default admin created: admin@aspcs.edu.in / Admin@1234");
            log.warn("⚠️  Change the default admin password in production!");
        }
    }
}
