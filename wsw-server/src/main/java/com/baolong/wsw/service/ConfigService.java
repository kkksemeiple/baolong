package com.baolong.wsw.service;

import com.baolong.wsw.entity.SystemConfig;
import com.baolong.wsw.mapper.SystemConfigMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统配置。改配置不需要重启，管理端改完立即生效（每条读都查库）。
 * <p>数据量只有十几条，没必要加缓存——加了反而要处理缓存一致性。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigService {

    private final SystemConfigMapper configMapper;

    /**
     * 各配置项的出厂值。
     * <p>用途有两个：读的时候库里没有就用它；管理端「参数配置」页拉取时也用它兜底，
     * 否则新增的配置项（比如 claimTimeoutHours）在旧库里还没那一行，
     * 前端会收到缺少的 key、渲染成 0，看起来像坏掉了。
     */
    private static final Map<String, String> DEFAULTS = Map.of(
            "initGrant", "100",
            "maxHolding", "100000",
            "grantLimit", "5000",
            "autoConfirmHours", "72",
            "claimTimeoutHours", "2",
            "penaltyCancel", "5",
            "penaltyNoShow", "20");

    public String get(String key, String defaultValue) {
        SystemConfig config = configMapper.selectById(key);
        return config == null || config.getValue() == null ? defaultValue : config.getValue();
    }

    public long getLong(String key, long defaultValue) {
        try {
            return Long.parseLong(get(key, String.valueOf(defaultValue)).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public int getInt(String key, int defaultValue) {
        return (int) getLong(key, defaultValue);
    }

    public Map<String, String> all() {
        List<SystemConfig> list = configMapper.selectList(Wrappers.<SystemConfig>lambdaQuery());
        Map<String, String> map = new LinkedHashMap<>();
        for (SystemConfig c : list) {
            map.put(c.getCfgKey(), c.getValue());
        }
        // 库里还没落行的配置项用出厂值补上，管理端页面才不会显示成 0
        DEFAULTS.forEach(map::putIfAbsent);
        return map;
    }

    public void update(String key, String value) {
        SystemConfig config = configMapper.selectById(key);
        if (config == null) {
            SystemConfig created = new SystemConfig();
            created.setCfgKey(key);
            created.setValue(value);
            created.setRemark("管理端新增");
            configMapper.insert(created);
        } else {
            config.setValue(value);
            configMapper.updateById(config);
        }
        log.info("系统配置更新: {}={}", key, value);
    }
}
