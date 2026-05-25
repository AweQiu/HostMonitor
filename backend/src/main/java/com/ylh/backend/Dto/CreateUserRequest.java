package com.ylh.backend.Dto;

import lombok.Data;

@Data
public class CreateUserRequest {
    private String username;
    private String email;
    private String password;
    private Integer status;
    private Long groupId;
    private String roleKey;
}
