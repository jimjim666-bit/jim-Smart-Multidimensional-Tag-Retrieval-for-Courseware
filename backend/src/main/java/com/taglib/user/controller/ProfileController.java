package com.taglib.user.controller;

import com.taglib.common.result.Result;
import com.taglib.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final UserService userService;

    @GetMapping("/my-resources")
    public Result<?> myResources(HttpSession session,
                                 @RequestParam(value = "page", defaultValue = "1") Integer page,
                                 @RequestParam(value = "page_size", defaultValue = "20") Integer pageSize) {
        Long userId = (Long) session.getAttribute("userId");
        return Result.ok(userService.myResources(userId, page, pageSize));
    }

    @GetMapping("/my-favorites")
    public Result<?> myFavorites(HttpSession session,
                                 @RequestParam(value = "page", defaultValue = "1") Integer page,
                                 @RequestParam(value = "page_size", defaultValue = "20") Integer pageSize) {
        Long userId = (Long) session.getAttribute("userId");
        return Result.ok(userService.myFavorites(userId, page, pageSize));
    }
}
