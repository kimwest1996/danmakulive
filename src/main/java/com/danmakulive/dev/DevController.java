package com.danmakulive.dev;

import com.danmakulive.auth.context.UserHolder;
import com.danmakulive.auth.model.dto.AuthResponse;
import com.danmakulive.auth.model.dto.RegisterRequest;
import com.danmakulive.auth.model.dto.UserDTO;
import com.danmakulive.auth.service.AuthService;
import com.danmakulive.common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@Profile("dev")
@RestController
@RequestMapping("/dev")
public class DevController {

    private static final Logger log = LoggerFactory.getLogger(DevController.class);

    private final AuthService authService;

    public DevController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/whoami")
    public Result<?> whoami() {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.success("not logged in");
        }
        return Result.success(user);
    }

    @PostMapping("/quick-register")
    public Result<?> quickRegister(@RequestParam(defaultValue = "dev") String name) {
        RegisterRequest req = new RegisterRequest();
        req.setEmail(name + "@danmakulive.dev");
        req.setPassword("123456");
        req.setNickname(name);
        try {
            return Result.success(authService.register(req));
        } catch (Exception e) {
            return Result.failure("A000005", "用户已存在，请用 /api/auth/login 登录");
        }
    }
}
