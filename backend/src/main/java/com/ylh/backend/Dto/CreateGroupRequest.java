package com.ylh.backend.Dto;

import lombok.Data;

@Data
public class CreateGroupRequest {
    private String groupName;
    private String description;
    private Long adminId;
}
