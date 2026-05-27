package com.ylh.backend.Dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NotificationVO {

    private Long id;
    private String title;
    private String content;
    private String senderName;
    private Integer sendType;
    private String targetInfo;
    private Integer isRead;
    private LocalDateTime sendTime;
}
