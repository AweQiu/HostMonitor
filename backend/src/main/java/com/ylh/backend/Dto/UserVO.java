package com.ylh.backend.Dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserVO {
    private Long id;
    private String username;
    private String email;
    private String avatar;
    private Integer status;
    private Long groupId;
    private String groupName;
    private List<String> roles;
    private LocalDateTime createTime;
}
