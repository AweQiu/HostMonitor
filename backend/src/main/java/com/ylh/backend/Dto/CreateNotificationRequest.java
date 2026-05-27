package com.ylh.backend.Dto;

import lombok.Data;

import java.util.List;

@Data
public class CreateNotificationRequest {

    private String title;
    private String content;
    private Integer sendType;
    private Long targetGroupId;
    private List<Long> targetUserIds;
}
