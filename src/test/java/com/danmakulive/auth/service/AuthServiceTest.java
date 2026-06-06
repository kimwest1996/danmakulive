package com.danmakulive.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danmakulive.auth.model.dto.AuthResponse;
import com.danmakulive.auth.model.dto.LoginRequest;
import com.danmakulive.auth.model.dto.RegisterRequest;
import com.danmakulive.auth.model.dto.UserDTO;
import com.danmakulive.auth.model.entity.User;
import com.danmakulive.auth.model.mapper.UserMapper;
import com.danmakulive.common.exception.ClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private AuthService authService;
    private UserMapper userMapper;
    private StringRedisTemplate redis;
    private HashOperations<String, Object, Object> hashOps;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        userMapper = mock(UserMapper.class);
        redis = mock(StringRedisTemplate.class);
        hashOps = mock(HashOperations.class);
        when(redis.opsForHash()).thenReturn(hashOps);

        authService = new AuthService(userMapper, redis, new BCryptPasswordEncoder());
    }

    @Test
    void registerSuccess() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("test@example.com");
        req.setPassword("123456");
        req.setNickname("TestUser");

        when(userMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(false);
        when(userMapper.insert(any(User.class))).thenReturn(1);

        AuthResponse resp = authService.register(req);

        assertNotNull(resp.getToken());
        assertEquals("test@example.com", resp.getEmail());
        assertNotNull(resp.getUserId());
    }

    @Test
    void registerDuplicateEmail() {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("dup@example.com");
        req.setPassword("123456");
        req.setNickname("Dup");

        when(userMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(true);

        assertThrows(ClientException.class, () -> authService.register(req));
    }

    @Test
    void loginSuccess() {
        User user = new User();
        user.setId("user-1");
        user.setEmail("test@example.com");
        user.setPassword(new BCryptPasswordEncoder().encode("123456"));
        user.setNickname("TestUser");
        user.setStatus(0);

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        LoginRequest req = new LoginRequest();
        req.setEmail("test@example.com");
        req.setPassword("123456");

        AuthResponse resp = authService.login(req);

        assertNotNull(resp.getToken());
        assertEquals("user-1", resp.getUserId());
    }

    @Test
    void loginWrongPassword() {
        User user = new User();
        user.setId("user-1");
        user.setPassword(new BCryptPasswordEncoder().encode("correct"));
        user.setStatus(0);

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        LoginRequest req = new LoginRequest();
        req.setEmail("test@example.com");
        req.setPassword("wrong");

        assertThrows(ClientException.class, () -> authService.login(req));
    }

    @Test
    void resolveTokenSuccess() {
        Map<Object, Object> entries = new HashMap<>();
        entries.put("id", "user-1");
        entries.put("nickName", "Test");
        entries.put("avatarUrl", "");

        when(hashOps.entries("login:token:abc123")).thenReturn(entries);
        when(redis.expire(eq("login:token:abc123"), eq(30L), eq(TimeUnit.DAYS))).thenReturn(true);

        UserDTO dto = authService.resolveToken("abc123");

        assertNotNull(dto);
        assertEquals("user-1", dto.getId());
    }

    @Test
    void resolveTokenNotFound() {
        when(hashOps.entries("login:token:bad")).thenReturn(Map.of());
        assertNull(authService.resolveToken("bad"));
    }
}
