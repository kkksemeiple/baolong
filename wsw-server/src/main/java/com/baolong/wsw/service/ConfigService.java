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
