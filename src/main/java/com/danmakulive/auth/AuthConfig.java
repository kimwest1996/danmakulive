package com.danmakulive.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AuthConfig implements WebMvcConfigurer {

    private final AuthService authService;

    public AuthConfig(AuthService authService) {
        this.authService = authService;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TokenInterceptor(authService))
                .addPathPatterns("/**")
                .order(0);

        registry.addInterceptor(new AuthInterceptor())
                .excludePathPatterns(
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/rooms/**",
                        "/ws",
                        "/ws-raw",
                        "/ws/**",
                        "/static/**",
                        "/error"
                )
                .order(1);
    }
}
