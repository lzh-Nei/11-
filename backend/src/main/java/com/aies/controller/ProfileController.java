package com.aies.controller;

import com.aies.common.PermissionConstants;
import com.aies.common.Result;
import com.aies.dto.UpdateProfileRequest;
import com.aies.dto.UserVO;
import com.aies.security.PrincipalUtils;
import com.aies.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人资料自助接口（个人中心）：三角色均可用（user:me）。
 *
 * <p>仅允许修改姓名与手机号；用户名/邮箱/角色/状态不在此接口暴露。</p>
 */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@Validated
public class ProfileController {

    private final ProfileService profileService;

    @PreAuthorize("hasAuthority('" + PermissionConstants.USER_ME + "')")
    @PutMapping
    public Result<UserVO> update(@Valid @RequestBody UpdateProfileRequest request) {
        return Result.success(profileService.updateProfile(request, PrincipalUtils.current()));
    }
}
