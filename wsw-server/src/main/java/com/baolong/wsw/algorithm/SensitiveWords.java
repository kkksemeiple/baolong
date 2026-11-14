package com.baolong.wsw.algorithm;

import java.util.List;
import java.util.Locale;

/**
 * 敏感词初筛（先发后审）。
 *
 * <p>课程项目不做完整 DFA 词库，只用一份显式词表做「包含匹配」：
 * 命中后内容<b>照常发布</b>，同时往 {@code audit_item} 塞一条 PENDING，
 * 由管理员在「内容审核与仲裁」里决定下架还是通过。
 *
 * <p>为什么是「先发后审」而不是「命中即拒」：
 * 校园场景下误报代价高（比如「代课」这个词有时是老师发布的正常需求），
 * 直接拦截会让用户莫名其妙发不出去；先发后审既不阻断主流程，又留下了处置记录。
 *
 * <p>要换成配置化词库，只需把 {@link #WORDS} 改成从 {@code system_config} 读取，
 * 调用方无需改动。
 */
public final class SensitiveWords {

    private SensitiveWords() {
    }

    /** 词表：明显违规的学术作弊、刷单套现、赌博色情类 */
    private static final List<String> WORDS = List.of(
            "代考", "替考", "代写", "枪手", "包过", "作弊",
            "刷单", "刷好评", "套现", "洗钱",
            "赌博", "博彩", "私彩", "色情", "裸聊",
            "代课", "代打卡"
    );

    /**
     * 判断文本是否命中敏感词。
     *
     * @param texts 标题、正文等多个字段，任意一个命中即返回
     * @return 命中的词；没命中返回 {@code null}
     */
    public static String hit(String... texts) {
        if (texts == null) {
            return null;
        }
        for (String text : texts) {
            if (text == null || text.isBlank()) {
                continue;
            }
            String lower = text.toLowerCase(Locale.ROOT);
            for (String word : WORDS) {
                if (lower.contains(word)) {
                    return word;
                }
            }
        }
        return null;
    }
}
