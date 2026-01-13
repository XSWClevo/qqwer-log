package cn.mw.loganalysis.config.service;

import cn.mw.loganalysis.config.dto.FieldConfigRequest;
import cn.mw.loganalysis.config.dto.FieldConfigResponse;
import cn.mw.loganalysis.config.entity.UserFieldConfig;
import cn.mw.loganalysis.config.mapper.UserFieldConfigMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 用户字段配置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserFieldConfigService {

    private final UserFieldConfigMapper userFieldConfigMapper;

    /**
     * 日志表格所有可用字段
     */
    private static final List<String> LOG_AVAILABLE_FIELDS = Arrays.asList(
            "timestamp", "level", "host", "service", "source",
            "message", "facility", "procid", "sourceIp", "raw"
    );

    /**
     * 默认选择的字段
     */
    private static final List<String> DEFAULT_SELECTED_FIELDS = Arrays.asList(
            "timestamp", "level", "host", "service", "message"
    );

    /**
     * 获取用户字段配置
     */
    public FieldConfigResponse getFieldConfig(String username, String configType) {
        log.info("获取用户字段配置: username={}, configType={}", username, configType);

        LambdaQueryWrapper<UserFieldConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFieldConfig::getUsername, username)
                .eq(UserFieldConfig::getConfigType, configType);

        UserFieldConfig config = userFieldConfigMapper.selectOne(wrapper);

        FieldConfigResponse response = new FieldConfigResponse();
        response.setConfigType(configType);
        response.setAvailableFields(LOG_AVAILABLE_FIELDS);

        if (config != null) {
            response.setSelectedFields(config.getSelectedFields());
            response.setFieldOrder(config.getFieldOrder());
        } else {
            // 返回默认配置
            response.setSelectedFields(DEFAULT_SELECTED_FIELDS);
            response.setFieldOrder(DEFAULT_SELECTED_FIELDS);
        }

        return response;
    }

    /**
     * 保存用户字段配置
     */
    public void saveFieldConfig(String username, FieldConfigRequest request) {
        log.info("保存用户字段配置: username={}, configType={}", username, request.getConfigType());

        LambdaQueryWrapper<UserFieldConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFieldConfig::getUsername, username)
                .eq(UserFieldConfig::getConfigType, request.getConfigType());

        UserFieldConfig config = userFieldConfigMapper.selectOne(wrapper);

        if (config != null) {
            // 更新已有配置
            config.setSelectedFields(request.getSelectedFields());
            config.setFieldOrder(request.getFieldOrder());
            config.setUpdatedAt(LocalDateTime.now());
            userFieldConfigMapper.updateById(config);
        } else {
            // 创建新配置
            config = new UserFieldConfig();
            config.setUsername(username);
            config.setConfigType(request.getConfigType());
            config.setSelectedFields(request.getSelectedFields());
            config.setFieldOrder(request.getFieldOrder());
            config.setCreatedAt(LocalDateTime.now());
            config.setUpdatedAt(LocalDateTime.now());
            userFieldConfigMapper.insert(config);
        }

        log.info("用户字段配置保存成功");
    }

    /**
     * 重置用户字段配置为默认值
     */
    public void resetFieldConfig(String username, String configType) {
        log.info("重置用户字段配置: username={}, configType={}", username, configType);

        LambdaQueryWrapper<UserFieldConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFieldConfig::getUsername, username)
                .eq(UserFieldConfig::getConfigType, configType);

        userFieldConfigMapper.delete(wrapper);
        log.info("用户字段配置重置成功");
    }
}
