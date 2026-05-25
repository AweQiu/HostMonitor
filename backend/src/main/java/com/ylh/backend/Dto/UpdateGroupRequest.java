package com.ylh.backend.Dto;

import lombok.Data;

@Data
public class UpdateGroupRequest {
    private String groupName;
    private String description;
}
