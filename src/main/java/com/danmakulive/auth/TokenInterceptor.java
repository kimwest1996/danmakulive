package com.danmakulive.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

public class TokenInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TokenInterceptor.class);

    private final AuthService authService;

    public TokenInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        String token = request.getHeader("authorization");
        if (token == null || token.isEmpty()) {
            return true;
        }
        UserDTO user = authService.resolveToken(token);
        if (user == null) {
            return true;
        }
        UserHolder.saveUser(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserHolder.removeUser();
    }
}
