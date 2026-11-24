package com.aies.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 教师驳回申诉请求。 */
@Data
public class RejectAppealRequest {

    @NotBlank(message = "请填写驳回理由")
    @Size(min = 5, max = 500, message = "驳回理由需为5-500字")
    private String reason;
}
