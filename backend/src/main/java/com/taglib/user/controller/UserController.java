package com.taglib.user.controller;

import com.taglib.common.exception.BizException;
import com.taglib.common.result.Result;
import com.taglib.user.entity.User;
import com.taglib.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public Result<?> register(@RequestBody Map<String, String> body) {
        Map<String, Object> result = userService.register(
                body.get("username"),
                body.get("password"),
                body.get("nickname")
        );
        return Result.ok(result);
    }

    @PostMapping("/login")
    public Result<?> login(@RequestBody Map<String, String> body, HttpServletRequest request) {
        User user = userService.login(body.get("username"), body.get("password"));
        HttpSession session = request.getSession();
        // 登录成功后更换 session id，防止会话固定攻击
        request.changeSessionId();
        session.setAttribute("userId", user.getUserId());
        session.setAttribute("role", user.getRole());
        return Result.ok(Map.of(
                "userId", String.valueOf(user.getUserId()),
                "username", user.getUsername(),
                "nickname", user.getNickname(),
                "role", user.getRole()
        ));
    }

    @PostMapping("/logout")
    public Result<?> logout(HttpSession session) {
        session.invalidate();
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<?> me(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        User user = userService.getByUserId(userId);
        if (user == null) {
            throw new BizException("UNAUTHORIZED", "未登录或登录已过期");
        }
        return Result.ok(Map.of(
                "userId", String.valueOf(user.getUserId()),
                "username", user.getUsername(),
                "nickname", user.getNickname(),
                "role", user.getRole()
        ));
    }
}
