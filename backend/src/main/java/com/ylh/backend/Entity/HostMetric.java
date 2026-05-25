package com.ylh.backend.Entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("host_metric")
public class HostMetric {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long hostId;
    private BigDecimal cpuUsage;
    private BigDecimal memoryUsage;
    private BigDecimal diskUsage;
    private Long memoryUsed;
    private Long diskUsed;
    private LocalDateTime collectTime;
}
