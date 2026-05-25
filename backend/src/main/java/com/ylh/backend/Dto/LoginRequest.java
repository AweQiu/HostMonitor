package com.ylh.backend.Dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String username;
    private String password;
}
