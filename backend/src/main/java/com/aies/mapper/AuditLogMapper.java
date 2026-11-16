package com.aies.mapper;

import com.aies.entity.AuditLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 审计日志Mapper（仅INSERT+SELECT——S-09仅追加红线，
 * 代码层不提供update/delete调用面）。
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
