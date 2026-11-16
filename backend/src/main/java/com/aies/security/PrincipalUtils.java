package com.aies.security;

import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 认证上下文工具（M3 管理端）：统一取当前登录主体与客户端 IP。
 *
 * <p>主体由 JwtAuthenticationFilter 装配；未登录路径不可达（入口层 401 兜底）。
 * IP 口径与 AuthController 一致：X-Forwarded-For 首段优先，回退 remoteAddr。</p>
 */
public final class PrincipalUtils {

    private PrincipalUtils() {
    }

    /** 当前登录主体；缺失即 40101（防御性，正常链路应由 Security 拦截） */
    public static LoginPrincipal current() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof LoginPrincipal principal)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return principal;
    }

    /** 客户端 IP（非 Web 上下文返回 null，供审计留痕） */
    public static String clientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
