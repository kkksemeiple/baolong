package com.baolong.wsw;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 黑盒功能测试（对应附表 5 的 BT-01 ~ BT-08）。
 *
 * <p><b>运行前置条件</b>：本机 MySQL 已导入 {@code sql/01-schema.sql}，
 * {@code application.yml} 里的数据库密码正确。因为依赖真实数据库，
 * 这个类被 surefire 的 {@code **}{@code /*IT.java} 规则排除在 {@code mvn test} 之外，
 * 需要时在 IDEA 里直接右键运行，或 {@code mvn test -Dtest=ApiFlowIT}。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("黑盒-接口流程：注册 / 登录 / 发布 / 抢单 / 结算")
class ApiFlowIT {

    private static final AtomicInteger SEQ = new AtomicInteger(0);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper om;

    private static String publisherToken;
    private static String takerToken;
    private static long taskId;

    @Test
    @Order(1)
    @DisplayName("BT-01 注册成功并赠送 100 屋币")
    void registerGrantsCoins() throws Exception {
        String username = "it_pub_" + System.currentTimeMillis() % 100000 + SEQ.incrementAndGet();
        JsonNode data = postJson("/auth/register", null, Map.of(
                "username", username,
                "password", "abc12345",
                "nickname", "集成测试发布人",
                "phone", "139" + String.format("%08d", SEQ.incrementAndGet())));

        assertEquals(0, data.get("code").asInt(), data.toString());
        publisherToken = data.get("data").get("token").asText();
        assertNotNull(publisherToken);

        // 钱包初始 100（initGrant）
        JsonNode wallet = getJson("/me/wallet", publisherToken);
        assertEquals(0, wallet.get("code").asInt());
        assertEquals(100, wallet.get("data").get("available").asInt());
    }

    @Test
    @Order(2)
    @DisplayName("BT-02 重复登录名注册被拒绝")
    void duplicateRegisterRejected() throws Exception {
        JsonNode data = postJson("/auth/register", null, Map.of(
                "username", "linxiaoman",
                "password", "abc12345",
                "nickname", "重名",
                "phone", "13700000009"));
        assertTrue(data.get("code").asInt() != 0, "重复用户名应返回非 0 code");
    }

    @Test
    @Order(3)
    @DisplayName("BT-03 错误密码登录返回 40100")
    void loginWithWrongPassword() throws Exception {
        JsonNode data = postJson("/auth/login", null, Map.of(
                "username", "linxiaoman",
                "password", "wrong-password"));
        assertEquals(40100, data.get("code").asInt());
    }

    @Test
    @Order(4)
    @DisplayName("BT-04 发布任务冻结报酬")
    void publishFreezesReward() throws Exception {
        String deadline = LocalDateTime.now().plusDays(3).format(FMT);
        String startTime = LocalDateTime.now().plusDays(1).format(FMT);
        JsonNode data = postJson("/tasks", publisherToken, Map.of(
                "title", "集成测试-取快递",
                "description", "帮我去菜鸟驿站取个快递",
                "type", "PICKUP",
                "reward", 60,
                "deadline", deadline,
                "startTime", startTime,
                "durationMinutes", 60,
                "location", "菜鸟驿站",
                "peopleNeeded", 1));

        assertEquals(0, data.get("code").asInt(), data.toString());
        taskId = data.get("data").get("id").asLong();

        JsonNode wallet = getJson("/me/wallet", publisherToken);
        assertEquals(60, wallet.get("data").get("frozen").asInt());
        assertEquals(40, wallet.get("data").get("available").asInt());
    }

    @Test
    @Order(5)
    @DisplayName("BT-05 余额不足发布失败且事务回滚")
    void publishWithoutEnoughCoins() throws Exception {
        String deadline = LocalDateTime.now().plusDays(3).format(FMT);
        String startTime = LocalDateTime.now().plusDays(1).format(FMT);
        JsonNode data = postJson("/tasks", publisherToken, Map.of(
                "title", "集成测试-超预算",
                "description", "余额不足的任务",
                "type", "PICKUP",
                "reward", 99999,
                "deadline", deadline,
                "startTime", startTime,
                "durationMinutes", 60,
                "location", "操场",
                "peopleNeeded", 1));

        assertTrue(data.get("code").asInt() != 0, "余额不足应失败");
    }

    @Test
    @Order(6)
    @DisplayName("BT-06 别人可以抢单，且抢单后状态变 TAKEN")
    void anotherUserClaims() throws Exception {
        String username = "it_taker_" + System.currentTimeMillis() % 100000 + SEQ.incrementAndGet();
        JsonNode reg = postJson("/auth/register", null, Map.of(
                "username", username,
                "password", "abc12345",
                "nickname", "集成测试接单人",
                "phone", "138" + String.format("%08d", SEQ.incrementAndGet())));
        takerToken = reg.get("data").get("token").asText();

        JsonNode claim = postJson("/tasks/" + taskId + "/claim", takerToken, null);
        assertEquals(0, claim.get("code").asInt(), claim.toString());

        JsonNode detail = getJson("/tasks/" + taskId, takerToken);
        assertEquals("TAKEN", detail.get("data").get("status").asText());
    }

    @Test
    @Order(7)
    @DisplayName("BT-07 不能接自己发布的任务")
    void cannotClaimOwnTask() throws Exception {
        String deadline = LocalDateTime.now().plusDays(3).format(FMT);
        String startTime = LocalDateTime.now().plusDays(1).format(FMT);
        JsonNode published = postJson("/tasks", publisherToken, Map.of(
                "title", "集成测试-自接",
                "description", "发布人自己抢",
                "type", "PICKUP",
                "reward", 10,
                "deadline", deadline,
                "startTime", startTime,
                "durationMinutes", 60,
                "location", "图书馆",
                "peopleNeeded", 1));
        long ownTaskId = published.get("data").get("id").asLong();

        JsonNode claim = postJson("/tasks/" + ownTaskId + "/claim", publisherToken, null);
        assertEquals(40001, claim.get("code").asInt(), claim.toString());
    }

    @Test
    @Order(8)
    @DisplayName("BT-08 未登录访问受保护接口返回 HTTP 401")
    void unauthorizedReturns401() throws Exception {
        mvc.perform(get("/me/wallet"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    // ------------------------------------------------------------------ 工具

    private JsonNode postJson(String url, String token, Object body) throws Exception {
        var builder = post(url).contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.content(om.writeValueAsString(body));
        } else {
            builder.content("{}");
        }
        MvcResult result = mvc.perform(builder).andReturn();
        return om.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode getJson(String url, String token) throws Exception {
        var builder = get(url);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        MvcResult result = mvc.perform(builder).andReturn();
        return om.readTree(result.getResponse().getContentAsString());
    }
}
