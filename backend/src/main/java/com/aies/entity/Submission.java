package com.aies.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 作业提交记录（版本化，V3__submission.sql）。
 *
 * <p>version_no 同任务同人递增（uk_task_student_version 唯一键兜底并发）；
 * file_key 当前为本地 FileStoreService 的 UUID32 文件id（与 MinIO 对象 key 同形，
 * 桶化迁移时存储层替换、key 语义不变——W6-C2 偏差登记）；
 * file_type 字典仅 docx/pdf（DB CHECK + 应用层 S-07 双重白名单）；
 * parse_status 字典 PENDING/QUEUED/SUCCESS/FAILED（§7.1 V1.2 四态）。</p>
 */
@Data
@TableName("submission")
public class Submission {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务id（超截止→40002，应用层校验） */
    private Long taskId;

    /** 学生id（仅本人，IDOR防线S-05） */
    private Long studentId;

    /** 存储对象key（UUID重命名，用户输入禁入key——S-07） */
    private String fileKey;

    /** 原始文件名（展示用） */
    private String fileName;

    /** 文件字节（≤20MB应用层校验→30003） */
    private Long fileSize;

    /** 字典：docx/pdf 白名单 */
    private String fileType;

    /** 提交版本：同任务同人递增 */
    private Integer versionNo;

    /** 解析状态机：PENDING/QUEUED/SUCCESS/FAILED */
    private String parseStatus;

    /** 提取文本对象key（W7解析域写入） */
    private String parsedTextKey;

    /** 1=学生取消本次提交（不进入教师队列） */
    private Integer canceled;

    private LocalDateTime cancelTime;

    private LocalDateTime createTime;
}
