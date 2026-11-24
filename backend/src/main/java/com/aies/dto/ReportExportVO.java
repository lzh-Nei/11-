package com.aies.dto;

import java.time.LocalDateTime;

/**
 * 异步报表导出任务状态（仅任务创建人可见）。
 */
public record ReportExportVO(Long id, String status, String fileName, String errorMsg,
                             LocalDateTime createTime, LocalDateTime finishTime) {
}
