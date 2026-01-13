package cn.mw.loganalysis.config.service;

import cn.mw.loganalysis.config.dto.SystemConfigDTO;
import cn.mw.loganalysis.config.dto.UpdateConfigRequest;
import cn.mw.loganalysis.config.dto.UpdateSystemConfigRequest;
import cn.mw.loganalysis.config.entity.ConfigHistory;
import cn.mw.loganalysis.config.entity.SystemConfig;
import cn.mw.loganalysis.config.mapper.SystemConfigMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统配置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigMapper systemConfigMapper;

    /**
     * 获取所有配置
     */
    public List<SystemConfig> getAllConfigs() {
        return systemConfigMapper.selectList(null);
    }

    /**
     * 根据 key 获取配置
     */
    public SystemConfig getByKey(String key) {
        List<SystemConfig> configs = systemConfigMapper.selectList(null);
        return configs.stream()
            .filter(c -> c.getConfigKey().equals(key))
            .findFirst()
            .orElse(null);
    }

    /**
     * 更新单个配置
     */
    @Transactional(rollbackFor = Exception.class)
    public SystemConfig updateConfig(String key, UpdateConfigRequest request, Long userId) {
        SystemConfig config = getByKey(key);
        if (config == null) {
            throw new IllegalArgumentException("配置不存在: " + key);
        }

        String oldValue = config.getConfigValue();
        config.setConfigValue(request.getConfigValue());
        config.setUpdatedAt(LocalDateTime.now());
        systemConfigMapper.updateById(config);

        // TODO: 记录配置历史
        log.info("配置已更新: key={}, oldValue={}, newValue={}, userId={}",
            key, oldValue, request.getConfigValue(), userId);

        return config;
    }

    /**
     * 获取配置历史
     */
    public Page<ConfigHistory> getHistory(String configKey, int pageNum, int pageSize) {
        // TODO: 实现配置历史查询
        return new Page<>(pageNum, pageSize);
    }

    /**
     * 获取指定类型的所有配置
     */
    public Map<String, String> getConfigByType(String configType) {
        List<SystemConfig> configs = systemConfigMapper.selectByConfigType(configType);
        return configs.stream()
                .collect(Collectors.toMap(
                        SystemConfig::getConfigKey,
                        SystemConfig::getConfigValue
                ));
    }

    /**
     * 获取指定类型的所有配置（包含描述）
     */
    public List<SystemConfigDTO> getConfigDTOByType(String configType) {
        List<SystemConfig> configs = systemConfigMapper.selectByConfigType(configType);
        return configs.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 更新配置
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateConfig(UpdateSystemConfigRequest request) {
        String configType = request.getConfigType();
        Map<String, String> configs = request.getConfigs();

        for (Map.Entry<String, String> entry : configs.entrySet()) {
            String configKey = entry.getKey();
            String configValue = entry.getValue();

            SystemConfig existing = systemConfigMapper.selectByTypeAndKey(configType, configKey);
            if (existing != null) {
                existing.setConfigValue(configValue);
                existing.setUpdatedAt(LocalDateTime.now());
                systemConfigMapper.updateById(existing);
            } else {
                SystemConfig newConfig = new SystemConfig();
                newConfig.setConfigKey(configKey);
                newConfig.setConfigValue(configValue);
                newConfig.setConfigType(configType);
                newConfig.setCreatedAt(LocalDateTime.now());
                newConfig.setUpdatedAt(LocalDateTime.now());
                systemConfigMapper.insert(newConfig);
            }
        }

        log.info("更新系统配置成功，类型：{}，配置项数量：{}", configType, configs.size());
    }

    /**
     * 获取单个配置值
     */
    public String getConfigValue(String configType, String configKey) {
        SystemConfig config = systemConfigMapper.selectByTypeAndKey(configType, configKey);
        return config != null ? config.getConfigValue() : null;
    }

    /**
     * 获取单个配置值（带默认值）
     */
    public String getConfigValue(String configType, String configKey, String defaultValue) {
        String value = getConfigValue(configType, configKey);
        return value != null ? value : defaultValue;
    }

    /**
     * Entity 转 DTO
     */
    private SystemConfigDTO toDTO(SystemConfig entity) {
        SystemConfigDTO dto = new SystemConfigDTO();
        dto.setConfigKey(entity.getConfigKey());
        dto.setConfigValue(entity.getConfigValue());
        dto.setDescription(entity.getDescription());
        return dto;
    }
}
