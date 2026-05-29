package com.ylh.backend.Dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProcessRuleVO {
    private Long id;
    private Long groupId;
    private String groupName;
    private String processName;
    private String ruleType;
    private String description;
    private LocalDateTime createTime;
}
