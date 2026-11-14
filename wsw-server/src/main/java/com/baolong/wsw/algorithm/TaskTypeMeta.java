package com.baolong.wsw.algorithm;

import com.baolong.wsw.enums.TaskType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 六类任务的元数据。
 * <p>「接发任务」模块的核心设计：六项核心属性（时间模式、地点、报酬、重叠策略、资格、凭证）
 * 全部集中在这一张表里，发布表单渲染、详情展示、资格校验、交付流程都以它为唯一数据源。
 * <p>好处是业务代码里没有一处 {@code if (type == PICKUP)}，新增任务类型只改这里的一行。
 *
 * @param timeMode      时间模式：POINT 单点 / RANGE 时间窗 / FLEXIBLE 不限
 * @param needLocation  是否必须填地点（例如「抢课」类不需要）
 * @param allowBargain  是否允许议价（只有金额类才允许）
 * @param needProof     交付时是否必须上传凭证
 * @param overlapPolicy 时间重叠策略：EXCLUSIVE 必须独占 / ALLOW_NEIGHBOR 允许与邻接任务共存
 * @param qualification 接单资格：NONE / REALNAME 需实名 / HIGH_CREDIT 需高信用
 */
public record TaskTypeMeta(
        TaskType type,
        TimeMode timeMode,
        boolean needLocation,
        boolean allowBargain,
        boolean needProof,
        OverlapPolicy overlapPolicy,
        Qualification qualification) {

    public enum TimeMode {
        /** 单点时间（代取、代买） */
        POINT,
        /** 时间段（家教、租赁） */
        RANGE,
        /** 不限（其他） */
        FLEXIBLE
    }

    public enum OverlapPolicy {
        /** 时间必须独占：与已有任务重叠即阻断 */
        EXCLUSIVE,
        /** 允许邻接：仅提示，不阻断 */
        ALLOW_NEIGHBOR
    }

    public enum Qualification {
        NONE,
        REALNAME,
        HIGH_CREDIT
    }

    private static final Map<TaskType, TaskTypeMeta> REGISTRY = new LinkedHashMap<>();

    static {
        registry(new TaskTypeMeta(TaskType.PICKUP, TimeMode.POINT, true, true, true,
                OverlapPolicy.EXCLUSIVE, Qualification.NONE));
        registry(new TaskTypeMeta(TaskType.BUY, TimeMode.POINT, true, true, true,
                OverlapPolicy.EXCLUSIVE, Qualification.NONE));
        registry(new TaskTypeMeta(TaskType.RENT, TimeMode.RANGE, true, true, true,
                OverlapPolicy.EXCLUSIVE, Qualification.REALNAME));
        registry(new TaskTypeMeta(TaskType.TUTOR, TimeMode.RANGE, true, true, true,
                OverlapPolicy.EXCLUSIVE, Qualification.REALNAME));
        registry(new TaskTypeMeta(TaskType.MOVE, TimeMode.POINT, true, true, true,
                OverlapPolicy.EXCLUSIVE, Qualification.NONE));
        registry(new TaskTypeMeta(TaskType.OTHER, TimeMode.FLEXIBLE, false, true, false,
                OverlapPolicy.ALLOW_NEIGHBOR, Qualification.NONE));
    }

    private static void registry(TaskTypeMeta meta) {
        REGISTRY.put(meta.type(), meta);
    }

    /** 取某个类型的元数据；未知类型退回「其他」 */
    public static TaskTypeMeta of(TaskType type) {
        return REGISTRY.getOrDefault(type, REGISTRY.get(TaskType.OTHER));
    }

    /** 全部类型元数据，供 /tasks/categories 接口使用 */
    public static Map<TaskType, TaskTypeMeta> all() {
        return Map.copyOf(REGISTRY);
    }
}
