package com.ylh.backend.Entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("host_process")
public class HostProcess {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long hostId;
    private Integer pid;
    private Integer ppid;
    private String name;
    private String user;
    private BigDecimal cpuUsage;
    private BigDecimal memoryUsage;
    private Long memoryUsed;
    private String status;
    private LocalDateTime startTime;
    private String commandLine;
    private LocalDateTime collectTime;
}
