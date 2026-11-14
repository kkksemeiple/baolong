package com.baolong.wsw;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 接口契约守门员（不需要数据库）。
 *
 * <p>后端 DTO 的字段名必须和前端 {@code vue-wsw/src/api/types.ts} 完全一致。
 * 这份期望值由 {@code D:\zhn\_tmp\contract_check.py} 从 types.ts 抽出后写进
 * {@code src/test/resources/contract.json}；本测试用反射核对后端 record 的
 * 组件名，任何一方改了字段名而没同步，这里立刻红。
 *
 * <p>为什么要这个测试：本项目联调阶段 7 个 bug 全是「前端叫 A、后端叫 B」，
 * 靠点按钮才发现。把它变成 compile/test 级别的失败，成本从「联调半天」降到「保存即知」。
 *
 * <p>改了契约怎么办：
 * <ol>
 *   <li>改前端 {@code types.ts}</li>
 *   <li>改后端 record</li>
 *   <li>跑 {@code python D:\zhn\_tmp\contract_check.py --write} 刷新快照</li>
 * </ol>
 */
@DisplayName("契约-前后端字段名一致性")
class ApiContractTest {

    private static final String DTO_PACKAGE = "com.baolong.wsw.dto.";

    @Test
    @DisplayName("CT-01 后端 DTO 字段与前端 types.ts 完全一致")
    void dtoFieldsMatchFrontendTypes() throws Exception {
        JsonNode contract = loadContract();
        assertNotNull(contract, "contract.json 不存在");
        int checked = 0;
        for (var it = contract.fields(); it.hasNext(); ) {
            var entry = it.next();
            String feName = entry.getKey();
            String javaName = entry.getValue().get("java").asText();
            Set<String> expected = new TreeSet<>();
            entry.getValue().get("fields").forEach(node -> expected.add(node.asText()));

            Class<?> type = resolve(javaName);
            if (type == null) {
                fail("找不到后端 record：" + javaName + "（对应前端 " + feName + "）");
            }
            if (!type.isRecord()) {
                fail(javaName + " 不是 record，无法核对字段");
            }
            Set<String> actual = new LinkedHashSet<>();
            for (RecordComponent component : type.getRecordComponents()) {
                actual.add(component.getName());
            }

            Set<String> missing = new TreeSet<>(expected);
            missing.removeAll(actual);
            Set<String> extra = new TreeSet<>(actual);
            extra.removeAll(expected);

            if (!missing.isEmpty() || !extra.isEmpty()) {
                fail("契约不一致：" + feName + " <-> " + javaName
                        + "\n  后端缺少字段: " + missing
                        + "\n  前端缺少字段: " + extra);
            }
            checked++;
        }
        assertEquals(9, checked, "契约条目数变了，确认是否漏登记 MAPPING");
    }

    // ------------------------------------------------------------------ 工具

    private JsonNode loadContract() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("contract.json")) {
            if (in == null) {
                return null;
            }
            return new ObjectMapper().readTree(in);
        }
    }

    /** {@code TaskDto.TaskVO} → {@code com.baolong.wsw.dto.TaskDto$TaskVO} */
    private Class<?> resolve(String javaName) {
        String fqcn = DTO_PACKAGE + javaName.replace(".", "$");
        try {
            return Class.forName(fqcn);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @SuppressWarnings("unused")
    private static Set<String> asSet(String... values) {
        return new TreeSet<>(Arrays.asList(values));
    }
}
