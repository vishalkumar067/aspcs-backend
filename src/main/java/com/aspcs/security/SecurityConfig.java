package com.aspcs.security;

import com.aspcs.auth.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthService           authService;
    private final JwtAuthFilter         jwtAuthFilter;
    private final PasswordEncoder       passwordEncoder;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ── CORS must come FIRST so preflight OPTIONS requests are handled ──
            .cors(cors -> cors.configurationSource(corsConfigurationSource))

            // ── Disable CSRF (stateless JWT API) ──
            .csrf(AbstractHttpConfigurer::disable)

            // ── Session management ──
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ── Route permissions ──
            .authorizeHttpRequests(auth -> auth

                // Allow OPTIONS preflight for ALL paths
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // Public auth
                .requestMatchers("/auth/**").permitAll()

                // Public read endpoints
                .requestMatchers(HttpMethod.GET, "/notices/public/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/notices/public").permitAll()
                .requestMatchers(HttpMethod.GET, "/gallery/public/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/gallery/public").permitAll()
                .requestMatchers(HttpMethod.GET, "/events/public/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/events").permitAll()
                .requestMatchers(HttpMethod.GET, "/careers/jobs").permitAll()
                .requestMatchers(HttpMethod.GET, "/careers/jobs/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/careers/jobs/*/apply").permitAll()
                .requestMatchers(HttpMethod.POST, "/admissions").permitAll()
                .requestMatchers(HttpMethod.POST, "/tc/submit").permitAll()

                // Everything else requires authentication
                .anyRequest().authenticated()
            )

            // ── JWT filter ──
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(authService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
