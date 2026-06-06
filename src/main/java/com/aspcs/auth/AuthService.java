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
