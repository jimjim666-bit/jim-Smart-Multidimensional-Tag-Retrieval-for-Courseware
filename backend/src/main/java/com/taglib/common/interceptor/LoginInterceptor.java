package com.taglib.common.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taglib.common.filter.RequestIdFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行OPTIONS预检
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 用 getSession(false) 避免为匿名请求创建会话
        HttpSession session = request.getSession(false);
        Long userId = session == null ? null : (Long) session.getAttribute("userId");
        if (userId == null) {
            writeJson(request, response, 401, "UNAUTHORIZED", "未登录或登录已过期");
            return false;
        }

        // 每次请求校验账号状态并读取最新角色：被禁用立即失效，角色变更即时生效
        Map<String, Object> account;
        try {
            account = jdbcTemplate.queryForMap(
                    "SELECT role, status FROM user WHERE user_id = ?", userId);
        } catch (EmptyResultDataAccessException e) {
            account = null;
        }
        if (account == null) {
            session.invalidate();
            writeJson(request, response, 401, "UNAUTHORIZED", "未登录或登录已过期");
            return false;
        }
        if ("DISABLED".equals(account.get("status"))) {
            session.invalidate();
            writeJson(request, response, 401, "AUTH_ACCOUNT_DISABLED", "账号已被禁用，请联系管理员");
            return false;
        }
        String role = (String) account.get("role");

        // admin接口校验
        if (pathMatcher.match("/api/admin/**", request.getRequestURI()) && !"ADMIN".equals(role)) {
            writeJson(request, response, 403, "ADMIN_REQUIRED", "需要管理员权限");
            return false;
        }

        // 仅对读请求校验资源可见性；PUT/DELETE/PATCH 等写操作（含子资源绑定）
        // 由对应 Service 校验 owner_id，防止公开资源被他人修改
        if ("GET".equalsIgnoreCase(request.getMethod()) || "HEAD".equalsIgnoreCase(request.getMethod())) {
            Long resourceId = extractResourceId(request.getRequestURI());
            if (resourceId != null && !checkResourcePermission(resourceId, userId, role)) {
                writeJson(request, response, 403, "RESOURCE_FORBIDDEN", "资源不存在或无权访问");
                return false;
            }
        }

        return true;
    }

    private Long extractResourceId(String uri) {
        // 匹配 /api/resources/123 或 /api/resources/123/download
        String[] parts = uri.split("/");
        if (parts.length >= 4 && "api".equals(parts[1]) && "resources".equals(parts[2])) {
            try {
                return Long.parseLong(parts[3]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private boolean checkResourcePermission(Long resourceId, Long userId, String role) {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT owner_id, visibility, status FROM resource WHERE resource_id = ? AND deleted_at IS NULL",
                resourceId
            );
            String visibility = (String) row.get("visibility");
            String status = (String) row.get("status");
            Long ownerId = ((Number) row.get("owner_id")).longValue();

            // owner 可查看自己非删除的资源（含 UPLOADING/PROCESSING/FAILED/BLOCKED），
            // 避免“我的资源列表可见、详情却 403”；BLOCKED 由前端标注“已下架”
            if (ownerId.equals(userId)) return true;
            if ("PUBLIC".equals(visibility) && "ACTIVE".equals(status)) return true;
            if ("ADMIN".equals(role)) return true;
            return false;
        } catch (EmptyResultDataAccessException e) {
            // 资源不存在（或已逻辑删除）按无权处理；数据库故障等异常交给全局异常处理器返回 500
            return false;
        }
    }

    private void writeJson(HttpServletRequest request, HttpServletResponse response,
                           int httpStatus, String code, String message) throws Exception {
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("data", null);
        Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        if (requestId != null) {
            body.put("requestId", requestId.toString());
        }
        PrintWriter w = response.getWriter();
        w.write(objectMapper.writeValueAsString(body));
        w.flush();
    }
}
