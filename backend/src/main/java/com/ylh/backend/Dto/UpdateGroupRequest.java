package com.ylh.backend.Dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateGroupRequest {
    @NotBlank(message = "分组名称不能为空")
    private String groupName;
    private String description;
}
