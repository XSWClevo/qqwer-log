package cn.mw.loganalysis.vector.service;

import cn.mw.loganalysis.vector.dto.AddConfigRequest;
import cn.mw.loganalysis.vector.entity.VectorConfig;
import cn.mw.loganalysis.vector.mapper.VectorConfigMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Vector配置管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorConfigService {

    private final VectorConfigMapper vectorConfigMapper;

    /**
     * 分页查询配置列表
     */
    public Page<VectorConfig> getConfigPage(int pageNum, int pageSize, String keyword, Boolean isTemplate) {
        Page<VectorConfig> page = new Page<>(pageNum, pageSize);
        return vectorConfigMapper.selectPageByCondition(page, keyword, isTemplate);
    }

    /**
     * 根据ID查询配置
     */
    public VectorConfig getConfigById(String id) {
        return vectorConfigMapper.selectById(id);
    }

    /**
     * 查询模板配置列表
     */
    public List<VectorConfig> getTemplates() {
        return vectorConfigMapper.selectTemplates();
    }

    /**
     * 添加配置
     */
    @Transactional(rollbackFor = Exception.class)
    public VectorConfig addConfig(AddConfigRequest request, String userId) {
        VectorConfig config = new VectorConfig();
        BeanUtils.copyProperties(request, config);

        // 自动生成版本号
        VectorConfig latestVersion = vectorConfigMapper.selectLatestVersion(config.getName());
        if (latestVersion != null) {
            config.setVersion(latestVersion.getVersion() + 1);
        } else {
            config.setVersion(1);
        }

        if (config.getIsTemplate() == null) {
            config.setIsTemplate(false);
        }

        config.setCreatedBy(userId);

        vectorConfigMapper.insert(config);
        log.info("添加Vector配置成功: {} v{}", config.getName(), config.getVersion());
        return config;
    }

    /**
     * 更新配置
     */
    @Transactional(rollbackFor = Exception.class)
    public VectorConfig updateConfig(String id, AddConfigRequest request) {
        VectorConfig config = vectorConfigMapper.selectById(id);
        if (config == null) {
            throw new RuntimeException("配置不存在");
        }

        BeanUtils.copyProperties(request, config);
        vectorConfigMapper.updateById(config);
        log.info("更新Vector配置成功: {}", config.getName());
        return config;
    }

    /**
     * 删除配置
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteConfig(String id) {
        VectorConfig config = vectorConfigMapper.selectById(id);
        if (config == null) {
            throw new RuntimeException("配置不存在");
        }

        vectorConfigMapper.deleteById(id);
        log.info("删除Vector配置成功: {}", config.getName());
    }

    /**
     * 复制配置（创建新版本）
     */
    @Transactional(rollbackFor = Exception.class)
    public VectorConfig copyConfig(String id, String userId) {
        VectorConfig sourceConfig = vectorConfigMapper.selectById(id);
        if (sourceConfig == null) {
            throw new RuntimeException("源配置不存在");
        }

        VectorConfig newConfig = new VectorConfig();
        BeanUtils.copyProperties(sourceConfig, newConfig);
        newConfig.setId(null);
        newConfig.setParentConfigId(id);
        newConfig.setCreatedBy(userId);

        // 生成新版本号
        VectorConfig latestVersion = vectorConfigMapper.selectLatestVersion(newConfig.getName());
        if (latestVersion != null) {
            newConfig.setVersion(latestVersion.getVersion() + 1);
        }

        vectorConfigMapper.insert(newConfig);
        log.info("复制Vector配置成功: {} v{}", newConfig.getName(), newConfig.getVersion());
        return newConfig;
    }
}
