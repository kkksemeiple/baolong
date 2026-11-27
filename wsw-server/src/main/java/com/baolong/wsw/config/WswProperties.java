package com.baolong.wsw.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 业务配置，对应 application.yml 里的 {@code wsw.*}。
 */
@Data
@Component
@ConfigurationProperties(prefix = "wsw")
public class WswProperties {

    private Jwt jwt = new Jwt();
    private Business business = new Business();

    @Data
    public static class Jwt {
        /**
         * 签名密钥。默认留空 = 启动时由 {@code JwtUtil} 生成随机密钥并落盘，
         * 部署环境请通过环境变量 WSW_JWT_SECRET 注入。
         */
        private String secret = "";
        /** 有效期（秒） */
        private long expireSeconds = 604800;
    }

    @Data
    public static class Business {
        /** 新用户注册赠送 */
        private long initGrant = 100;
        /** 单人持有上限 */
        private long maxHolding = 100000;
        /** 单次发放上限 */
        private long grantLimit = 5000;
        /** 待确认自动确认小时数 */
        private int autoConfirmHours = 72;
        /** 违约金-取消 */
        private long penaltyCancel = 5;
        /** 违约金-爽约 */
        private long penaltyNoShow = 20;
        /** 低于该分限制接单 */
        private int creditLimit = 80;
        /** 新手期单量：不足该单量走先验平滑 */
        private int creditWarmup = 20;
        /** 统计窗口天数 */
        private int creditWindowDays = 90;
    }
}
