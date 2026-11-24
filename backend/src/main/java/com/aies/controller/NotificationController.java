package com.aies.controller;

import com.aies.common.Result;
import com.aies.dto.NotificationVO;
import com.aies.security.PrincipalUtils;
import com.aies.service.NotificationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 站内通知接口：列表 / 未读数 / 单条已读 / 全部已读。
 *
 * <p>通知只对当前登录用户可见，无需额外权限点（登录即可）。</p>
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Validated
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public Result<List<NotificationVO>> list(
            @RequestParam(value = "size", defaultValue = "30") @Min(1) @Max(100) int size) {
        return Result.success(notificationService.list(PrincipalUtils.current().userId(), size));
    }

    @GetMapping("/unread-count")
    public Result<Map<String, Long>> unreadCount() {
        long unread = notificationService.unreadCount(PrincipalUtils.current().userId());
        return Result.success(Map.of("unread", unread));
    }

    @PostMapping("/{id}/read")
    public Result<Integer> markRead(@PathVariable("id") @Positive long id) {
        return Result.success(notificationService.markRead(PrincipalUtils.current().userId(), id));
    }

    @PostMapping("/read-all")
    public Result<Integer> markAllRead() {
        return Result.success(notificationService.markAllRead(PrincipalUtils.current().userId()));
    }
}
