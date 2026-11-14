package com.baolong.wsw;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 信誉分明细接口的集成测试（需要本机 MySQL，已导入 wsw 库）。
 * <p>用真实库跑一遍「登录 → 查 /users/{id}/credit」，
 * 校验返回结构、分数与等级的对应关系、以及记录与统计口径是否自洽。
 * <p>surefire 默认排除 {@code *IT}，手动执行：
 * <pre>mvn test -Dtest=CreditDetailIT</pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
class CreditDetailIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String login() throws Exception {
        String body = """
                {"username":"admin","password":"abc12345"}
                """;
        String json = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(json);
        assertEquals(0, node.path("code").asInt(), "登录失败：" + json);
        return node.path("data").path("token").asText();
    }

    private JsonNode credit(String token, long userId) throws Exception {
        String json = mockMvc.perform(get("/users/" + userId + "/credit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(json);
        assertEquals(0, node.path("code").asInt(), "接口返回异常：" + json);
        return node.path("data");
    }

    @Test
    @DisplayName("信誉分明细：结构完整、等级与分数一致、统计口径自洽")
    void creditDetailIsConsistent() throws Exception {
        String token = login();

        JsonNode d = credit(token, 1L);

        // 1. 必备字段齐全
        for (String field : new String[]{"score", "level", "levelLabel", "finishRate", "onTimeRate",
                "reviewScore", "disputeRate", "cancelRate", "totalClaims", "finishedClaims",
                "windowDays", "canAccept", "records"}) {
            assertTrue(d.has(field), "缺少字段：" + field);
        }

        double score = d.path("score").asDouble();
        assertTrue(score >= 0 && score <= 120, "分数应落在 0~120，实际 " + score);

        // 2. 等级由分数推导，且 canAccept 与等级一致
        String level = d.path("level").asText();
        String expected = score >= 110 ? "EXCELLENT"
                : score >= 95 ? "GOOD"
                : score >= 80 ? "NORMAL"
                : score >= 60 ? "LIMITED" : "FROZEN";
        assertEquals(expected, level, "等级与分数不匹配");
        assertEquals(score >= 80, d.path("canAccept").asBoolean(), "canAccept 与等级不匹配");

        // 3. 完成数不会超过总单量；比率都在 0~1
        assertTrue(d.path("finishedClaims").asInt() <= d.path("totalClaims").asInt(),
                "完成数不应大于总单量");
        assertTrue(d.path("totalClaims").asInt() >= d.path("records").size(),
                "记录条数不应超过窗口内总单量（最多返回 20 条）");
        for (String rate : new String[]{"finishRate", "onTimeRate", "disputeRate", "cancelRate"}) {
            double v = d.path(rate).asDouble();
            assertTrue(v >= 0 && v <= 1, rate + " 应为 0~1，实际 " + v);
        }
        double review = d.path("reviewScore").asDouble();
        assertTrue(review >= 0 && review <= 5, "平均评价应为 0~5，实际 " + review);
        assertTrue(d.path("windowDays").asInt() > 0, "统计窗口天数应大于 0");

        // 4. 记录字段齐全（有记录时）
        JsonNode records = d.path("records");
        if (records.size() > 0) {
            JsonNode first = records.get(0);
            for (String field : new String[]{"taskId", "taskTitle", "status", "statusLabel",
                    "rating", "claimedAt", "submittedAt", "onTime"}) {
                assertTrue(first.has(field), "记录缺少字段：" + field);
            }
            assertNotNull(first.path("taskTitle").asText());
        }
    }

    @Test
    @DisplayName("信誉分明细：他人也能查，且未登录返回 401")
    void creditDetailAccess() throws Exception {
        String token = login();
        // 换一个用户 id 也能查（公开信息）
        JsonNode other = credit(token, 2L);
        assertTrue(other.has("score"));

        // 未带 token → 真正的 HTTP 401（前端据此清凭证跳登录）
        mockMvc.perform(get("/users/1/credit"))
                .andExpect(status().isUnauthorized());
    }
}
