package com.danmakulive.auth.config;

import com.danmakulive.auth.interceptor.AuthInterceptor;
import com.danmakulive.auth.interceptor.TokenInterceptor;
import com.danmakulive.auth.service.AuthService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AuthConfig implements WebMvcConfigurer {

    private final AuthService authService;

    public AuthConfig(AuthService authService) {
        this.authService = authService;
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
                        "/api/rooms",
                        "/api/rooms/*",
                        "/api/rooms/*/danmaku/history",
                        "/ws",
                        "/ws-raw",
                        "/ws/**",
                        "/static/**",
                        "/error"
                )
                .order(1);
    }
}
