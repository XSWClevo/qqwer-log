package cn.mw.loganalysis.vector.service;

import cn.mw.loganalysis.vector.dto.DeployConfigRequest;
import cn.mw.loganalysis.vector.entity.MachineConfig;
import cn.mw.loganalysis.vector.entity.VectorDeployment;
import cn.mw.loganalysis.vector.entity.VectorMachine;
import cn.mw.loganalysis.vector.entity.VisualConfig;
import cn.mw.loganalysis.vector.mapper.MachineConfigMapper;
import cn.mw.loganalysis.vector.mapper.VectorDeploymentMapper;
import cn.mw.loganalysis.vector.mapper.VectorMachineMapper;
import cn.mw.loganalysis.vector.mapper.VisualConfigMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Vector配置部署服务
 * 
 * 部署方式：Agent 拉取模式 + Config-Dir 结构
 * - 支持多个配置同时部署到一台机器
 * - 每个配置作为独立的 pipeline
 * - 使用 config-dir 模式避免配置覆盖
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorDeploymentService {

    private final VectorDeploymentMapper deploymentMapper;
    private final VectorMachineMapper machineMapper;
    private final MachineConfigMapper machineConfigMapper;
    private final ConfigDirGeneratorService configDirGeneratorService;
    private final VisualConfigMapper visualConfigMapper;
    private final VisualConfigYamlService visualConfigYamlService;

    /**
     * 创建部署任务（Pipeline 模式）
     * 将配置作为独立 pipeline 部署，不影响其他配置
     */
    @Transactional(rollbackFor = Exception.class)
    public List<VectorDeployment> createDeployments(DeployConfigRequest request, String userId) {
        List<VectorDeployment> deployments = new ArrayList<>();
        String configContent = resolveConfigContent(request.getConfigId());
        
        // 生成配置版本号
        String configVersion = UUID.randomUUID().toString().substring(0, 8) + "-" + System.currentTimeMillis();

        for (String hostId : request.getHostIds()) {
            VectorMachine machine = machineMapper.selectById(hostId);
            if (machine == null) {
                log.warn("机器不存在: {}", hostId);
                continue;
            }

            VectorDeployment deployment = new VectorDeployment();
            deployment.setMachineId(hostId);
            deployment.setConfigId(request.getConfigId());
            deployment.setConfigContent(configContent);
            deployment.setConfigVersion(configVersion);
            deployment.setDeployMode(request.getDeployMode());
            deployment.setStatus("pending");
            deployment.setCreatedBy(userId);

            deploymentMapper.insert(deployment);
            deployments.add(deployment);

            // 更新机器配置关系
            updateMachineConfig(hostId, request.getConfigId(), configVersion);

            log.info("创建部署任务: 机器={}, 配置={}, 版本={}", machine.getName(), request.getConfigId(), configVersion);
        }

        return deployments;
    }

    private String resolveConfigContent(String configId) {
        VisualConfig config = visualConfigMapper.selectById(configId);
        if (config == null) {
            throw new RuntimeException("配置不存在: " + configId);
        }

        if (StringUtils.isNotBlank(config.getContent())) {
            return config.getContent();
        }

        String content = visualConfigYamlService.generateContentFromGraphData(config.getGraphData());
        if (StringUtils.isBlank(content)) {
            throw new RuntimeException("配置内容为空，请先保存可视化配置");
        }

        config.setContent(content);
        visualConfigMapper.updateById(config);
        return content;
    }

    /**
     * 更新机器配置关系
     */
    private void updateMachineConfig(String machineId, String configId, String configVersion) {
        MachineConfig existing = machineConfigMapper.selectByMachineAndConfig(machineId, configId);
        if (existing != null) {
            existing.setStatus("pending");
            existing.setDeployedVersion(configVersion);
            machineConfigMapper.updateById(existing);
        } else {
            MachineConfig machineConfig = new MachineConfig();
            machineConfig.setMachineId(machineId);
            machineConfig.setConfigId(configId);
            machineConfig.setStatus("pending");
            machineConfig.setDeployedVersion(configVersion);
            machineConfigMapper.insert(machineConfig);
        }
    }

    /**
     * Agent 拉取待部署的配置
     * 返回该机器最新的待部署配置
     */
    public VectorDeployment getPendingDeployment(String machineId) {
        return deploymentMapper.selectLatestPendingByMachineId(machineId);
    }

    /**
     * 获取机器上所有已部署配置的合并内容
     * 用于 Agent 获取完整的 config-dir 结构
     */
    public Map<String, String> getMergedConfigDir(String machineId) {
        return configDirGeneratorService.generateConfigDir(machineId);
    }

    /**
     * 从部署记录生成 config-dir 结构
     * 直接使用部署记录中的配置内容，不依赖数据库状态
     */
    public Map<String, String> generateConfigDirFromDeployment(VectorDeployment deployment) {
        return configDirGeneratorService.generateConfigDirFromContent(
                deployment.getMachineId(),
                deployment.getConfigId(), 
                deployment.getConfigContent()
        );
    }

    /**
     * Agent 上报部署状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateDeploymentStatus(String deploymentId, String status, String errorMessage) {
        VectorDeployment deployment = deploymentMapper.selectById(deploymentId);
        if (deployment == null) {
            log.warn("部署记录不存在: {}", deploymentId);
            return;
        }

        deployment.setStatus(status);
        if ("deploying".equals(status)) {
            deployment.setStartedAt(LocalDateTime.now());
        } else if ("success".equals(status) || "failed".equals(status)) {
            deployment.setFinishedAt(LocalDateTime.now());
            if (errorMessage != null) {
                deployment.setErrorMessage(errorMessage);
            }
            
            // 更新机器配置关系状态
            MachineConfig machineConfig = machineConfigMapper.selectByMachineAndConfig(
                    deployment.getMachineId(), deployment.getConfigId());
            if (machineConfig != null) {
                machineConfig.setStatus("success".equals(status) ? "deployed" : "failed");
                if ("success".equals(status)) {
                    machineConfig.setDeployedAt(LocalDateTime.now());
                }
                machineConfigMapper.updateById(machineConfig);
            }
        }

        deploymentMapper.updateById(deployment);
        log.info("更新部署状态: id={}, status={}", deploymentId, status);
    }

    /**
     * 从机器上移除配置
     * 会触发重新部署，让 Agent 更新配置（不再包含被移除的配置）
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeConfigFromMachine(String machineId, String configId, String userId) {
        MachineConfig machineConfig = machineConfigMapper.selectByMachineAndConfig(machineId, configId);
        if (machineConfig == null) {
            log.warn("机器 {} 上没有配置 {}", machineId, configId);
            return;
        }
        
        // 1. 删除关联记录
        machineConfigMapper.deleteById(machineConfig.getId());
        log.info("从机器 {} 移除配置 {}", machineId, configId);
        
        // 2. 检查该机器是否还有其他配置
        List<VisualConfig> remainingConfigs = machineConfigMapper.selectDeployedConfigsByMachineId(machineId);
        
        if (remainingConfigs.isEmpty()) {
            // 没有其他配置了，可以考虑停止 Vector 或保持空配置
            log.info("机器 {} 上没有其他配置了", machineId);
            // TODO: 可以创建一个"清空配置"的部署任务
        } else {
            // 3. 创建重新部署任务，让 Agent 更新配置
            String configVersion = "remove-" + configId.substring(0, 8) + "-" + System.currentTimeMillis();
            
            VectorDeployment deployment = new VectorDeployment();
            deployment.setMachineId(machineId);
            deployment.setConfigId(remainingConfigs.get(0).getId()); // 使用第一个剩余配置的 ID
            deployment.setConfigContent(""); // 内容由 getMergedConfigDir 生成
            deployment.setConfigVersion(configVersion);
            deployment.setDeployMode("restart");
            deployment.setStatus("pending");
            deployment.setCreatedBy(userId);
            
            deploymentMapper.insert(deployment);
            log.info("创建重新部署任务: 机器={}, 版本={}, 移除配置={}", machineId, configVersion, configId);
        }
    }
    
    /**
     * 从机器上移除配置（无用户信息版本，兼容旧调用）
     */
    @Transactional(rollbackFor = Exception.class)
    public void removeConfigFromMachine(String machineId, String configId) {
        removeConfigFromMachine(machineId, configId, "system");
    }

    /**
     * 获取机器上已部署的配置列表
     */
    public List<VisualConfig> getDeployedConfigs(String machineId) {
        return machineConfigMapper.selectDeployedConfigsByMachineId(machineId);
    }

    /**
     * 查询部署记录
     */
    public Page<VectorDeployment> getDeployments(int pageNum, int pageSize,
                                                  String machineId, String configId, String status) {
        Page<VectorDeployment> page = new Page<>(pageNum, pageSize);
        return deploymentMapper.selectPageByCondition(page, machineId, configId, status);
    }

    /**
     * 根据ID查询部署记录
     */
    public VectorDeployment getDeploymentById(String id) {
        return deploymentMapper.selectById(id);
    }

    /**
     * 根据机器ID查询部署记录
     */
    public List<VectorDeployment> getDeploymentsByMachineId(String machineId) {
        return deploymentMapper.selectByMachineId(machineId);
    }

    /**
     * 根据配置版本查询部署状态
     */
    public List<VectorDeployment> getDeploymentsByConfigVersion(String configVersion) {
        return deploymentMapper.selectByConfigVersion(configVersion);
    }

    /**
     * 为多台机器重新生成并下发配置
     * 用于配置删除后，更新受影响机器的配置
     */
    @Transactional(rollbackFor = Exception.class)
    public void redeployMachineConfigs(List<String> machineIds, String userId) {
        String configVersion = "redeploy-" + UUID.randomUUID().toString().substring(0, 8) + "-" + System.currentTimeMillis();

        for (String machineId : machineIds) {
            VectorMachine machine = machineMapper.selectById(machineId);
            if (machine == null) {
                log.warn("机器不存在: {}", machineId);
                continue;
            }

            // 检查该机器是否还有其他配置
            List<VisualConfig> remainingConfigs = machineConfigMapper.selectDeployedConfigsByMachineId(machineId);

            if (remainingConfigs.isEmpty()) {
                log.info("机器 {} 上没有配置了，跳过重新部署", machine.getName());
                continue;
            }

            // 创建重新部署任务
            VectorDeployment deployment = new VectorDeployment();
            deployment.setMachineId(machineId);
            deployment.setConfigId(remainingConfigs.get(0).getId()); // 使用第一个配置的 ID
            deployment.setConfigContent(""); // 内容由 getMergedConfigDir 生成
            deployment.setConfigVersion(configVersion);
            deployment.setDeployMode("restart");
            deployment.setStatus("pending");
            deployment.setCreatedBy(userId);

            deploymentMapper.insert(deployment);
            log.info("创建重新部署任务: 机器={}, 版本={}, 剩余配置数={}",
                    machine.getName(), configVersion, remainingConfigs.size());
        }
    }

    /**
     * 为指定机器重新下发全量配置（包括内部 pipeline）
     * 不依赖可视化配置表，直接创建 pending 部署记录，由 Agent 拉取时通过 getMergedConfigDir 生成最新配置
     */
    @Transactional(rollbackFor = Exception.class)
    public VectorDeployment redeployAllConfig(String machineId, String userId) {
        VectorMachine machine = machineMapper.selectById(machineId);
        if (machine == null) {
            throw new RuntimeException("机器不存在: " + machineId);
        }

        String configVersion = "redeploy-all-" + UUID.randomUUID().toString().substring(0, 8) + "-" + System.currentTimeMillis();

        VectorDeployment deployment = new VectorDeployment();
        deployment.setMachineId(machineId);
        deployment.setConfigId("_system_redeploy");
        deployment.setConfigContent("");
        deployment.setConfigVersion(configVersion);
        deployment.setDeployMode("restart");
        deployment.setStatus("pending");
        deployment.setCreatedBy(userId);

        deploymentMapper.insert(deployment);
        log.info("创建全量重新部署任务: 机器={}, 版本={}", machine.getName(), configVersion);

        return deployment;
    }
}
