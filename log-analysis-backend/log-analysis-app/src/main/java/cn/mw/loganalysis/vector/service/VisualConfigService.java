package cn.mw.loganalysis.vector.service;

import cn.mw.loganalysis.vector.dto.CreateVisualConfigRequest;
import cn.mw.loganalysis.vector.dto.UpdateVisualConfigRequest;
import cn.mw.loganalysis.vector.dto.ValidateConfigResponse;
import cn.mw.loganalysis.vector.entity.MachineConfig;
import cn.mw.loganalysis.vector.entity.VisualConfig;
import cn.mw.loganalysis.vector.mapper.MachineConfigMapper;
import cn.mw.loganalysis.vector.mapper.VisualConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 可视化配置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisualConfigService {

    private final VisualConfigMapper visualConfigMapper;
    private final MachineConfigMapper machineConfigMapper;
    private final VectorDeploymentService deploymentService;
    private final VisualConfigYamlService visualConfigYamlService;

    @Value("${vector.binary-path:vector}")
    private String vectorBinaryPath;

    /**
     * 查询配置列表
     */
    public List<VisualConfig> getConfigList(String keyword) {
        return visualConfigMapper.selectByCondition(keyword);
    }

    /**
     * 根据ID查询配置
     */
    public VisualConfig getConfigById(String id) {
        return visualConfigMapper.selectById(id);
    }

    /**
     * 创建配置
     */
    @Transactional(rollbackFor = Exception.class)
    public VisualConfig createConfig(CreateVisualConfigRequest request, String userId) {
        VisualConfig config = new VisualConfig();
        BeanUtils.copyProperties(request, config);
        config.setNodeCount(0);
        config.setCreatedBy(userId);
        config.setGraphData("{}");
        config.setContent("");

        visualConfigMapper.insert(config);
        log.info("创建可视化配置成功: {}", config.getName());
        return config;
    }

    /**
     * 更新配置
     */
    @Transactional(rollbackFor = Exception.class)
    public VisualConfig updateConfig(String id, UpdateVisualConfigRequest request) {
        VisualConfig config = visualConfigMapper.selectById(id);
        if (config == null) {
            throw new RuntimeException("配置不存在");
        }

        if (request.getName() != null) {
            config.setName(request.getName());
        }
        if (request.getDescription() != null) {
            config.setDescription(request.getDescription());
        }
        if (request.getGraphData() != null) {
            config.setGraphData(request.getGraphData());
            config.setContent(generateContentFromGraphData(request.getGraphData()));
        }
        if (request.getContent() != null) {
            config.setContent(request.getContent());
        }
        if (request.getNodeCount() != null) {
            config.setNodeCount(request.getNodeCount());
        }

        visualConfigMapper.updateById(config);
        
        log.info("更新可视化配置成功: {}", config.getName());
        return config;
    }

    /**
     * 删除配置
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteConfig(String id) {
        VisualConfig config = visualConfigMapper.selectById(id);
        if (config == null) {
            throw new RuntimeException("配置不存在");
        }

        // 1. 查询该配置部署到了哪些机器
        List<MachineConfig> machineConfigs = machineConfigMapper.selectByConfigId(id);
        Set<String> affectedMachineIds = new HashSet<>();
        for (MachineConfig mc : machineConfigs) {
            affectedMachineIds.add(mc.getMachineId());
        }

        // 2. 删除机器配置关系
        for (MachineConfig mc : machineConfigs) {
            machineConfigMapper.deleteById(mc.getId());
            log.info("删除机器配置关系: machineId={}, configId={}", mc.getMachineId(), id);
        }

        // 3. 删除可视化配置
        visualConfigMapper.deleteById(id);
        log.info("删除可视化配置成功: {}", config.getName());

        // 4. 为受影响的机器重新生成配置并下发
        if (!affectedMachineIds.isEmpty()) {
            log.info("配置删除后，需要更新 {} 台机器的配置", affectedMachineIds.size());
            deploymentService.redeployMachineConfigs(new ArrayList<>(affectedMachineIds), "system");
        }
    }

    /**
     * 导出配置内容
     */
    public String exportConfig(String id) {
        VisualConfig config = visualConfigMapper.selectById(id);
        if (config == null) {
            throw new RuntimeException("配置不存在");
        }
        return config.getContent();
    }

    /**
     * 根据 graphData 生成 YAML 内容
     */
    public String generateContentFromGraphData(String graphData) {
        return visualConfigYamlService.generateContentFromGraphData(graphData);
    }

    /**
     * 使用 Vector 命令行校验配置
     */
    public ValidateConfigResponse validateConfig(String content) {
        if (content == null || content.trim().isEmpty()) {
            return ValidateConfigResponse.fail("配置内容不能为空");
        }

        Path tempFile = null;
        try {
            // 修正纯数字密码等字段：确保 password 值为 YAML 字符串
            content = fixYamlStringFields(content);

            // 创建临时文件
            tempFile = Files.createTempFile("vector-config-", ".yaml");
            Files.write(tempFile, content.getBytes(StandardCharsets.UTF_8));

            // 执行 vector validate 命令
            // 使用 --no-environment 跳过环境检查（包括 data_dir 检查）
            ProcessBuilder pb = new ProcessBuilder(
                vectorBinaryPath, "validate", 
                "--no-environment",
                "--config-yaml", tempFile.toString()
            );
            pb.redirectErrorStream(true);

            Process process = pb.start();
            
            // 读取输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // 等待进程完成（最多 30 秒）
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ValidateConfigResponse.fail("校验超时");
            }

            int exitCode = process.exitValue();
            String outputStr = output.toString().trim();
            
            if (exitCode == 0) {
                log.info("Vector 配置校验通过");
                return ValidateConfigResponse.success();
            } else {
                log.warn("Vector 配置校验失败: {}", outputStr);
                return ValidateConfigResponse.fail(outputStr);
            }

        } catch (IOException e) {
            log.error("执行 Vector 校验命令失败", e);
            return ValidateConfigResponse.fail("执行校验命令失败: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ValidateConfigResponse.fail("校验被中断");
        } finally {
            // 清理临时文件
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("删除临时配置文件失败: {}", tempFile);
                }
            }
        }
    }

    /**
     * 修正 YAML 中需要是字符串但可能被解析为数字/布尔的字段
     * 例如 password: 12345678 -> password: "12345678"
     */
    private String fixYamlStringFields(String yamlContent) {
        // 匹配 password/user/username 字段后跟纯数字或布尔值的情况，加上双引号
        return yamlContent.replaceAll(
            "(?m)^(\\s*(?:password|user|username):\\s*)([0-9]+(?:\\.[0-9]+)?|true|false|yes|no|on|off)\\s*$",
            "$1\"$2\""
        );
    }
}
