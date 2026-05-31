package com.ylh.backend.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CreateNotificationRequest {
    @NotBlank(message = "通知标题不能为空")
    private String title;
    @NotBlank(message = "通知内容不能为空")
    private String content;
    @NotNull(message = "请选择发送范围")
    private Integer sendType;
    private Long targetGroupId;
    private List<Long> targetUserIds;
}
