package com.aies.service;

import com.aies.common.BusinessException;
import com.aies.common.ErrorCode;
import com.aies.dto.UpdateProfileRequest;
import com.aies.dto.UserVO;
import com.aies.entity.AuditLog;
import com.aies.entity.User;
import com.aies.mapper.AuditLogMapper;
import com.aies.mapper.RbacMapper;
import com.aies.mapper.UserMapper;
import com.aies.security.LoginPrincipal;
import com.aies.security.PrincipalUtils;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 个人资料自助服务（个人中心）：姓名/手机号更新 + 审计留痕。
 *
 * <p>邮箱与用户名不可自助变更；手机号可清空（传空串）。更新仅写 real_name/phone，
 * 不触碰密码、状态、角色。</p>
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserMapper userMapper;
    private final RbacMapper rbacMapper;
    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public UserVO updateProfile(UpdateProfileRequest request, LoginPrincipal principal) {
        long userId = principal.userId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        String realName = request.getRealName() == null ? "" : request.getRealName().trim();
        if (realName.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "姓名不能为空");
        }
        String rawPhone = request.getPhone() == null ? "" : request.getPhone().trim();
        if (!rawPhone.isEmpty() && !rawPhone.matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "手机号格式不正确");
        }
        String phone = rawPhone.isEmpty() ? null : rawPhone;
        String beforeName = user.getRealName();
        String beforePhone = user.getPhone();

        userMapper.update(null, new UpdateWrapper<User>()
                .eq("id", userId)
                .set("real_name", realName)
                .set("phone", phone));

        audit(userId, beforeName, beforePhone, realName, phone);

        User updated = userMapper.selectById(userId);
        return UserVO.from(updated, rbacMapper.selectRoleCodes(userId), rbacMapper.selectPermCodes(userId));
    }

    /** S-09 留痕：手机号脱敏后落审计明细（PII 不落全量） */
    private void audit(long userId, String beforeName, String beforePhone,
                       String afterName, String afterPhone) {
        AuditLog audit = new AuditLog();
        audit.setOperatorId(userId);
        audit.setModule("user");
        audit.setAction("UPDATE_PROFILE");
        audit.setTarget("user:" + userId);
        ObjectNode detail = objectMapper.createObjectNode();
        detail.put("realNameBefore", beforeName);
        detail.put("realNameAfter", afterName);
        detail.put("phoneBefore", maskPhone(beforePhone));
        detail.put("phoneAfter", maskPhone(afterPhone));
        audit.setDetail(detail.toString());
        audit.setIp(PrincipalUtils.clientIp());
        auditLogMapper.insert(audit);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        if (phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
