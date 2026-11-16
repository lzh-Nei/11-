package com.aies.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 学生评价申诉请求。 */
@Data
public class EvaluationAppealRequest {

    @NotBlank(message = "请填写申诉理由")
    @Size(max = 500, message = "申诉理由最长500字")
    private String reason;
}
