package com.aies.dto;

import lombok.Data;

import java.util.List;

/**
 * 评价历史版本分页VO（03-03 §七 E-04；UC-25游标分页契约形状）。
 *
 * <p>cursor为不透明Base64串（服务端解码为起始version_no）；hasMore=false时
 * nextCursor为null；列表按version_no倒序返回。</p>
 */
@Data
public class EvaluationHistoryPageVO {

    /** 当前页版本列表（version_no倒序） */
    private List<EvaluationHistoryVO> list;

    /** 下一页游标（Base64；hasMore=false时null） */
    private String nextCursor;

    /** 是否还有后续页 */
    private boolean hasMore;
}
