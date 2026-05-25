package com.ylh.backend.Dto;

import lombok.Data;

import java.util.List;

@Data
public class AuthResponse {
    private String token;
    private UserDTO user;

    @Data
    public static class UserDTO {
        private Long id;
        private String username;
        private String email;
        private String avatar;
        private Integer status;
        private Long groupId;
        private List<String> roles;
    }
}
