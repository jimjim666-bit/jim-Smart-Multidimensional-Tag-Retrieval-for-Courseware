package com.taglib;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@SpringBootApplication
@MapperScan("com.taglib.**.mapper")
@EnableScheduling
public class TaglibApplication {

    static {
        // 与时间约定一致：JVM 固定 UTC，避免部署机（尤其是 Ubuntu）默认时区影响 Instant.now() 与 SQL 时间
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(TaglibApplication.class, args);
    }
}
