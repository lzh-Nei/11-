package com.aies.service;

import com.aies.entity.AuditLog;
import com.aies.mapper.AuditLogMapper;
import com.aies.security.LoginPrincipal;
import com.aies.security.PrincipalUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 管理端审计留痕（S-09 仅追加；UC-32/33/34/35/40）。
 *
 * <p>AdminController 的写操作统一调用；detail 以 JSON 串落库（审计表 detail 为 JSON 列），
 * IP 取请求上下文，非 Web 场景允许为空。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuditService {

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    public void record(LoginPrincipal principal, String module, String action,
                       String target, Map<String, Object> detail) {
        AuditLog audit = new AuditLog();
        audit.setOperatorId(principal.userId());
        audit.setModule(module);
        audit.setAction(action);
        audit.setTarget(target);
        try {
            audit.setDetail(detail == null ? null : objectMapper.writeValueAsString(detail));
        } catch (JsonProcessingException e) {
            log.warn("审计明细序列化失败（action={}, target={}）", action, target);
        }
        audit.setIp(PrincipalUtils.clientIp());
        auditLogMapper.insert(audit);
    }
}
