package com.aies.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 刷新令牌请求（03-03 A-03：持RT换新AT+新RT，旧RT即废；无效→40101）。
 */
public record RefreshRequest(
        @NotBlank(message = "refreshToken不能为空") String refreshToken) {
}
