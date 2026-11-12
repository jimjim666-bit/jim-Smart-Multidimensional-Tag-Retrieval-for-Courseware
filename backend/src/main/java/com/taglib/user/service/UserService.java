package com.taglib.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.taglib.common.exception.BizException;
import com.taglib.common.utils.PasswordUtil;
import com.taglib.user.entity.User;
import com.taglib.user.mapper.UserMapper;
import com.taglib.user.mapper.ProfileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final ProfileMapper profileMapper;

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{4,32}$");

    @Transactional
    public Map<String, Object> register(String username, String password, String nickname) {
        if (username == null || !USERNAME_PATTERN.matcher(username.trim()).matches()) {
            throw new BizException("PARAM_ERROR", "账号需为4~32位字母、数字或下划线");
        }
        username = username.trim();

        if (password == null || password.length() < 8 || password.length() > 64) {
            throw new BizException("PARAM_ERROR", "密码需为8~64个字符");
        }
        // BCrypt 只使用前 72 字节，超长密码会被静默截断，这里提前拒绝
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BizException("PARAM_ERROR", "密码超过72字节，请缩短后重试");
        }
        if (nickname == null || nickname.trim().length() < 2 || nickname.trim().length() > 30) {
            throw new BizException("PARAM_ERROR", "昵称需为2~30个字符");
        }

        User exist = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (exist != null) {
            throw new BizException("USERNAME_DUPLICATED", "账号已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(PasswordUtil.encode(password));
        user.setNickname(nickname.trim());
        user.setRole("USER");
        user.setStatus("ACTIVE");
        user.setFailedLoginCount(0);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发注册时预查询可能同时通过，由唯一索引兜底
            throw new BizException("USERNAME_DUPLICATED", "账号已存在");
        }

        return Map.of("userId", String.valueOf(user.getUserId()));
    }

    public User login(String username, String password) {
        if (username == null || password == null) {
            throw new BizException("AUTH_BAD_CREDENTIALS", "账号或密码错误");
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username.trim()));

        // 查不到用户统一返回密码错误
        if (user == null) {
            throw new BizException("AUTH_BAD_CREDENTIALS", "账号或密码错误");
        }

        if ("DISABLED".equals(user.getStatus())) {
            throw new BizException("AUTH_ACCOUNT_DISABLED", "账号已被禁用，请联系管理员");
        }

        if ("TEMP_LOCKED".equals(user.getStatus())) {
            Instant now = Instant.now();
            Instant lockedUntil = user.getLockedUntil();
            if (lockedUntil != null && lockedUntil.isAfter(now)) {
                throw lockedException(lockedUntil);
            }
            // 锁定已到期（或截止时间缺失）：先解除锁定并清零计数，再继续比对密码。
            // 影响行数为 0 说明状态已被并发请求改变，必须按库里最新状态重新分支，
            // 否则可能在“刚被重新锁定”的情况下继续比对密码并登录成功。
            if (userMapper.releaseExpiredLock(user.getUserId()) == 0) {
                User latest = userMapper.selectById(user.getUserId());
                if (latest == null || "DISABLED".equals(latest.getStatus())) {
                    throw new BizException("AUTH_ACCOUNT_DISABLED", "账号已被禁用，请联系管理员");
                }
                if ("TEMP_LOCKED".equals(latest.getStatus())) {
                    throw lockedException(latest.getLockedUntil());
                }
                user = latest;
            } else {
                user.setStatus("ACTIVE");
                user.setFailedLoginCount(0);
                user.setLockedUntil(null);
            }
        }

        if (!PasswordUtil.matches(password, user.getPasswordHash())) {
            // 数据库端原子累加，避免并发失败请求丢失计数；
            // 第 5 次失败在同一语句内置为 TEMP_LOCKED，同时避免整行回写覆盖其他字段
            userMapper.recordLoginFailure(user.getUserId());
            throw new BizException("AUTH_BAD_CREDENTIALS", "账号或密码错误");
        }

        // 登录成功：定向重置。影响行数为 0 说明账号在此期间被管理员禁用
        if (userMapper.resetLoginSuccess(user.getUserId()) == 0) {
            throw new BizException("AUTH_ACCOUNT_DISABLED", "账号已被禁用，请联系管理员");
        }

        return user;
    }

    /** 附带可重试时间，前端展示剩余等待时间（SRS 4.1.2）；locked_until 缺失时退化为普通锁定提示 */
    private BizException lockedException(Instant lockedUntil) {
        if (lockedUntil == null) {
            return new BizException("AUTH_ACCOUNT_LOCKED", "账号已临时锁定，请稍后再试");
        }
        return new BizException("AUTH_ACCOUNT_LOCKED", "账号已临时锁定，请稍后再试",
                Map.of("lockedUntil", lockedUntil.truncatedTo(ChronoUnit.SECONDS).toString()));
    }

    public User getByUserId(Long userId) {
        return userMapper.selectById(userId);
    }

    public Map<String, Object> myResources(Long userId, Integer page, Integer pageSize) {
        int[] p = normalizePage(page, pageSize);
        int offset = (p[0] - 1) * p[1];
        List<Map<String, Object>> list = profileMapper.selectMyResources(userId, offset, p[1]);
        Long total = profileMapper.countMyResources(userId);
        return buildPageResult(total, p[0], p[1], list);
    }

    public Map<String, Object> myFavorites(Long userId, Integer page, Integer pageSize) {
        int[] p = normalizePage(page, pageSize);
        int offset = (p[0] - 1) * p[1];
        List<Map<String, Object>> list = profileMapper.selectMyFavorites(userId, offset, p[1]);
        Long total = profileMapper.countMyFavorites(userId);
        return buildPageResult(total, p[0], p[1], list);
    }

    private int[] normalizePage(Integer page, Integer pageSize) {
        if (page == null || page < 1) {
            page = 1;
        }
        if (pageSize == null || (pageSize != 10 && pageSize != 20 && pageSize != 50)) {
            pageSize = 20;
        }
        return new int[]{page, pageSize};
    }

    private Map<String, Object> buildPageResult(Long total, int page, int pageSize, List<Map<String, Object>> list) {
        Map<String, Object> result = new HashMap<>();
        result.put("total", total == null ? 0 : total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("list", list);
        return result;
    }
}
