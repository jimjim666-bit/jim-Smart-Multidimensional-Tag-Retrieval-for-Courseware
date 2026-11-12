package com.taglib.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long userId;
    private String username;
    private String passwordHash;
    private String nickname;
    private String role;
    private String status;
    private Integer failedLoginCount;
    private Instant lockedUntil;
    @TableField(fill = FieldFill.INSERT)
    private Instant createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
