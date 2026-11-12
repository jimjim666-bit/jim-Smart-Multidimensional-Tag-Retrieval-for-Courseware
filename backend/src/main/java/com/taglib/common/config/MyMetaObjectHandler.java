package com.taglib.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    /** 时间戳精确到秒，避免 Instant.now() 的纳秒进入响应 JSON */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createdAt", Instant.class, now());
        this.strictInsertFill(metaObject, "updatedAt", Instant.class, now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", Instant.class, now());
    }
}
