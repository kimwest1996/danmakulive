package com.danmakulive.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.danmakulive.auth.model.dto.AuthResponse;
import com.danmakulive.auth.model.dto.LoginRequest;
import com.danmakulive.auth.model.dto.RegisterRequest;
import com.danmakulive.auth.model.dto.UserDTO;
import com.danmakulive.auth.model.entity.User;
import com.danmakulive.auth.model.mapper.UserMapper;
import com.danmakulive.common.exception.BaseErrorCode;
import com.danmakulive.common.exception.ClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String TOKEN_PREFIX = "login:token:";
    private static final long TOKEN_TTL_DAYS = 30;

    private final UserMapper userMapper;
    private final StringRedisTemplate redis;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthService(UserMapper userMapper,
                       StringRedisTemplate redis,
                       BCryptPasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.redis = redis;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthResponse register(RegisterRequest req) {
        if (req.getEmail() == null || !req.getEmail().contains("@")) {
            throw new ClientException("邮箱格式不正确", BaseErrorCode.VALIDATION_ERROR);
        }
        if (req.getPassword() == null || req.getPassword().length() < 6) {
            throw new ClientException("密码长度至少6位", BaseErrorCode.VALIDATION_ERROR);
        }
        String nickname = req.getNickname() != null ? req.getNickname().trim() : "";
        if (nickname.length() < 1 || nickname.length() > 32) {
            throw new ClientException("昵称长度1-32个字符", BaseErrorCode.VALIDATION_ERROR);
        }
        boolean exists = userMapper.exists(
                new LambdaQueryWrapper<User>().eq(User::getEmail, req.getEmail()));
        if (exists) {
            throw new ClientException("该邮箱已注册", BaseErrorCode.DUPLICATE);
        }

        User user = new User();
        user.setId(UUID.randomUUID().toString());
        user.setEmail(req.getEmail());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(nickname);
        user.setAvatarUrl("");
        user.setStatus(0);
        userMapper.insert(user);

        String token = UUID.randomUUID().toString().replace("-", "");
        saveToken(token, user);

        log.info("[DEV] User registered: token={} | userId={} | email={}", token, user.getId(), user.getEmail());
        return buildResponse(user, token);
    }

    public AuthResponse login(LoginRequest req) {
        if (req.getEmail() == null || req.getEmail().isBlank()) {
            throw new ClientException("邮箱不能为空", BaseErrorCode.VALIDATION_ERROR);
        }
        if (req.getPassword() == null || req.getPassword().isBlank()) {
            throw new ClientException("密码不能为空", BaseErrorCode.VALIDATION_ERROR);
        }

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, req.getEmail()));
        if (user == null) {
            throw new ClientException("邮箱或密码错误", BaseErrorCode.UNAUTHORIZED);
        }
        if (user.getStatus() != null && user.getStatus() != 0) {
            throw new ClientException("账号已被禁用", BaseErrorCode.FORBIDDEN);
        }
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new ClientException("邮箱或密码错误", BaseErrorCode.UNAUTHORIZED);
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        saveToken(token, user);

        log.info("[DEV] User logged in: token={} | userId={} | email={}", token, user.getId(), user.getEmail());
        return buildResponse(user, token);
    }

    public void logout(String token) {
        if (token != null && !token.isEmpty()) {
            redis.delete(TOKEN_PREFIX + token);
        }
    }

    public UserDTO resolveToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        Map<Object, Object> entries = redis.opsForHash().entries(TOKEN_PREFIX + token);
        if (entries.isEmpty()) {
            return null;
        }
        UserDTO dto = new UserDTO();
        dto.setId((String) entries.get("id"));
        dto.setNickName((String) entries.get("nickName"));
        dto.setAvatarUrl((String) entries.get("avatarUrl"));
        redis.expire(TOKEN_PREFIX + token, TOKEN_TTL_DAYS, TimeUnit.DAYS);
        return dto;
    }

    private void saveToken(String token, User user) {
        Map<String, String> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("nickName", user.getNickname());
        map.put("avatarUrl", user.getAvatarUrl());
        redis.opsForHash().putAll(TOKEN_PREFIX + token, map);
        redis.expire(TOKEN_PREFIX + token, TOKEN_TTL_DAYS, TimeUnit.DAYS);
    }

    private AuthResponse buildResponse(User user, String token) {
        AuthResponse resp = new AuthResponse();
        resp.setUserId(user.getId());
        resp.setEmail(user.getEmail());
        resp.setNickname(user.getNickname());
        resp.setAvatarUrl(user.getAvatarUrl());
        resp.setToken(token);
        return resp;
    }
}
