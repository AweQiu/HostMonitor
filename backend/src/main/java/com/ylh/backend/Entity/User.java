package com.ylh.backend.Entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("`user`")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    private String email;
    private String password;
    private String agentKey;
    private String avatar;
    private Integer status;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long groupId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDel;
}
