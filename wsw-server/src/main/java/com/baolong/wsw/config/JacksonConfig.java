package com.baolong.wsw.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 时间格式统一。
 * <p>出参统一 {@code yyyy-MM-dd HH:mm:ss}；入参同时兼容
 * {@code yyyy-MM-dd HH:mm:ss}、{@code yyyy-MM-dd'T'HH:mm:ss}、纯日期三种写法，
 * 这样前端 el-date-picker 无论配哪种 value-format 都能被接住。
 */
@Configuration
public class JacksonConfig {

    private static final DateTimeFormatter OUT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** LocalDate 只输出日期部分（趋势图的 X 轴用） */
    private static final DateTimeFormatter DATE_ONLY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer wswTimeCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(OUT));
            module.addDeserializer(LocalDateTime.class, new FlexibleLocalDateTimeDeserializer());
            module.addSerializer(LocalDate.class, new LocalDateSerializer(DATE_ONLY));
            // 必须用 modulesToInstall：它会「追加」到 Spring Boot 已注册的 JavaTimeModule 之后。
            // 用 modules(...) 是「替换」，会把 JavaTimeModule 顶掉，
            // 结果就是只补了 LocalDateTime 的序列化器，遇到 LocalDate（比如趋势图的日期）
            // 直接抛 InvalidDefinitionException → 500。
            builder.modulesToInstall(module);
            builder.featuresToDisable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        };
    }

    /**
     * 宽松的时间反序列化：多格式尝试，全失败才报错。
     */
    static class FlexibleLocalDateTimeDeserializer extends LocalDateTimeDeserializer {

        private static final DateTimeFormatter[] PATTERNS = {
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd")
        };

        FlexibleLocalDateTimeDeserializer() {
            super(OUT);
        }

        @Override
        public LocalDateTime deserialize(com.fasterxml.jackson.core.JsonParser p,
                                         com.fasterxml.jackson.databind.DeserializationContext ctxt)
                throws java.io.IOException {
            String text = p.getText();
            if (text == null || text.isBlank()) {
                return null;
            }
            String value = text.trim();

            // 1) 带时区的 ISO 字符串。前端的 new Date(x).toISOString() 产出
            //    2026-11-12T11:58:05.000Z，去掉 Z 之后还留着 .000，
            //    用下面的固定格式一定解析失败，所以先按 OffsetDateTime 处理，
            //    并转换到服务器时区（否则「明天 19:58」会被当成 11:58）。
            if (value.length() > 19 && (value.endsWith("Z") || value.matches(".*[+-]\\d{2}:?\\d{2}$"))) {
                try {
                    return java.time.OffsetDateTime.parse(value)
                            .atZoneSameInstant(java.time.ZoneId.systemDefault())
                            .toLocalDateTime();
                } catch (DateTimeParseException ignored) {
                    // 继续尝试其它格式
                }
            }

            // 2) 标准 ISO（可能带毫秒），例如 2026-11-12T19:58:05.000
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException ignored) {
                // 继续尝试其它格式
            }

            // 3) 固定格式（前端 el-date-picker 的 value-format 产物）
            for (DateTimeFormatter f : PATTERNS) {
                try {
                    if (f == PATTERNS[3]) {
                        return java.time.LocalDate.parse(value, f).atStartOfDay();
                    }
                    return LocalDateTime.parse(value, f);
                } catch (DateTimeParseException ignored) {
                    // 换下一个格式
                }
            }
            return super.deserialize(p, ctxt);
        }
    }
}
