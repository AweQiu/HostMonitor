package com.ylh.backend.Dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class GroupVO {
    private Long id;
    private String groupName;
    private String description;
    private Integer memberCount;
    private String adminName;
    private Long adminId;
    private LocalDateTime createTime;
}
