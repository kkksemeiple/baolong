package com.baolong.wsw;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 暴龙万事屋 —— 校园任务大厅后端服务入口。
 */
@EnableScheduling
@MapperScan("com.baolong.wsw.mapper")
@SpringBootApplication
public class WswApplication {

    public static void main(String[] args) {
        SpringApplication.run(WswApplication.class, args);
    }
}
