package com.ylh.backend.Dto;

import lombok.Data;

@Data
public class UpdateUserRequest {
    private String email;
    private String password;
    private Integer status;
    private Long groupId;
    private Boolean clearGroupId;
}
