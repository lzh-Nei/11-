package com.aies.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 异步报表导出任务（V1 report_export_task + V3 结果列）。
 *
 * <p>status 字典：QUEUED/GENERATING/DONE/FAILED；仅创建人可查询与下载。</p>
 */
@Data
@TableName("report_export_task")
public class ReportExportTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 幂等键（本次实现按次生成 UUID） */
    private String requestId;

    private Long requesterId;

    /** 筛选条件（当前仅存 scope：teacher/me） */
    private String filterJson;

    private String status;

    /** 导出结果对象 key（DONE 时非空） */
    private String fileKey;

    private String fileName;

    private String errorMsg;

    private LocalDateTime createTime;

    private LocalDateTime finishTime;
}
