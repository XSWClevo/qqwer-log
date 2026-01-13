package cn.mw.loganalysis.vector.service;

import cn.mw.loganalysis.vector.entity.VisualConfig;
import cn.mw.loganalysis.vector.mapper.MachineConfigMapper;
import cn.mw.loganalysis.vector.mapper.VisualConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.*;

/**
 * Config-Dir 配置生成服务
 * 
 * 生成 Vector config-dir 模式所需的扁平目录结构：
 * /config/
 * ├── global.yaml           (data_dir 等全局配置)
 * ├── pipeline_a_sources.yaml
 * ├── pipeline_a_transforms.yaml
 * ├── pipeline_a_sinks.yaml
 * ├── pipeline_b_sources.yaml
 * └── ...
 * 
 * 注意：Vector config-dir 不支持嵌套目录，必须使用扁平结构
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigDirGeneratorService {

    private final VisualConfigMapper visualConfigMapper;
    private final MachineConfigMapper machineConfigMapper;

    /**
     * 为指定机器生成完整的 config-dir 结构
     * 
     * @param machineId 机器ID
     * @return Map<文件路径, 文件内容>
     */
    public Map<String, String> generateConfigDir(String machineId) {
        Map<String, String> configFiles = new LinkedHashMap<>();
        
        // 1. 获取该机器已部署的所有配置
        List<VisualConfig> deployedConfigs = machineConfigMapper.selectDeployedConfigsByMachineId(machineId);
        
        log.info("机器 {} 已部署的配置数量: {}", machineId, deployedConfigs.size());
        for (VisualConfig config : deployedConfigs) {
            log.info("  - 配置: id={}, name={}, contentLength={}", 
                    config.getId(), config.getName(), 
                    config.getContent() != null ? config.getContent().length() : 0);
        }
        
        if (deployedConfigs.isEmpty()) {
            log.warn("机器 {} 没有已部署的配置", machineId);
            return configFiles;
        }
        
        // 2. 添加全局配置（包含 API 配置用于状态监控）
        configFiles.put("global.yaml", generateGlobalConfig());
        
        // 3. 为每个配置生成独立的组件文件
        for (VisualConfig config : deployedConfigs) {
            log.info("处理配置: {}, content:\n{}", config.getName(), config.getContent());
            generatePipelineFiles(configFiles, config.getName(), config.getContent());
        }
        
        log.info("为机器 {} 生成 {} 个配置文件: {}", machineId, configFiles.size(), configFiles.keySet());
        return configFiles;
    }

    /**
     * 为单个配置生成 pipeline 目录结构
     * 
     * @param configId 配置ID
     * @return Map<文件路径, 文件内容>
     */
    public Map<String, String> generatePipelineConfig(String configId) {
        Map<String, String> configFiles = new LinkedHashMap<>();
        
        VisualConfig config = visualConfigMapper.selectById(configId);
        if (config == null) {
            throw new RuntimeException("配置不存在: " + configId);
        }
        
        // 添加全局配置（包含 API 配置）
        configFiles.put("global.yaml", generateGlobalConfig());
        
        // 生成 pipeline 文件
        String pipelineName = getPipelineName(config);
        generatePipelineFiles(configFiles, pipelineName, config.getContent());
        
        return configFiles;
    }

    /**
     * 直接从配置内容生成 config-dir 结构
     * 不依赖数据库状态，用于部署时生成配置
     * 
     * @param configId 配置ID（用于获取 pipeline 名称）
     * @param yamlContent 配置内容
     * @return Map<文件路径, 文件内容>
     */
    public Map<String, String> generateConfigDirFromContent(String configId, String yamlContent) {
        Map<String, String> configFiles = new LinkedHashMap<>();
        
        if (yamlContent == null || yamlContent.trim().isEmpty()) {
            log.warn("配置内容为空: {}", configId);
            return configFiles;
        }
        
        // 添加全局配置（包含 API 配置）
        configFiles.put("global.yaml", generateGlobalConfig());
        
        // 获取 pipeline 名称
        String pipelineName;
        VisualConfig config = visualConfigMapper.selectById(configId);
        if (config != null) {
            pipelineName = getPipelineName(config);
        } else {
            // 如果找不到配置，使用 configId 作为 pipeline 名称
            pipelineName = configId.toLowerCase().replaceAll("[^a-z0-9_-]", "_");
        }
        
        // 生成 pipeline 文件
        generatePipelineFiles(configFiles, pipelineName, yamlContent);
        
        log.info("从配置内容生成 config-dir: configId={}, pipelineName={}, files={}", 
                configId, pipelineName, configFiles.size());
        
        return configFiles;
    }

    /**
     * 将配置内容拆分为 config-dir 结构
     * 
     * 目录结构：
     * config/
     * ├── sources/
     * │   └── component_name.yaml
     * ├── transforms/
     * │   └── component_name.yaml
     * └── sinks/
     *     └── component_name.yaml
     * 
     * 每个 yaml 文件内容不包含顶层 key，直接是组件配置
     */
    @SuppressWarnings("unchecked")
    private void generatePipelineFiles(Map<String, String> configFiles, String pipelineName, String yamlContent) {
        if (yamlContent == null || yamlContent.trim().isEmpty()) {
            return;
        }
        
        Yaml yaml = new Yaml();
        Map<String, Object> parsed;
        try {
            parsed = yaml.load(yamlContent);
        } catch (Exception e) {
            log.warn("解析 YAML 失败: {}", e.getMessage());
            return;
        }
        
        if (parsed == null) {
            return;
        }
        
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        Yaml dumper = new Yaml(options);
        
        // 遍历 sources, transforms, sinks
        for (String section : Arrays.asList("sources", "transforms", "sinks")) {
            if (parsed.containsKey(section)) {
                Map<String, Object> components = (Map<String, Object>) parsed.get(section);
                if (components != null && !components.isEmpty()) {
                    // 每个组件生成一个独立文件
                    for (Map.Entry<String, Object> entry : components.entrySet()) {
                        String componentName = entry.getKey();
                        Object componentConfig = entry.getValue();
                        // 文件路径: sources/component_name.yaml
                        String path = section + "/" + componentName + ".yaml";

                        // 对于 transforms 类型，检查是否需要特殊处理多行 source
                        String yamlConfigContent;
                        if ("transforms".equals(section) && componentConfig instanceof Map) {
                            yamlConfigContent = dumpTransformConfig((Map<String, Object>) componentConfig, dumper);
                        } else {
                            yamlConfigContent = dumper.dump(componentConfig);
                        }

                        configFiles.put(path, yamlConfigContent);
                    }
                }
            }
        }
    }

    /**
     * 处理 Transform 配置的 YAML 序列化
     * 对于包含多行 source 字段的 remap transform，使用字面量块样式
     */
    @SuppressWarnings("unchecked")
    private String dumpTransformConfig(Map<String, Object> config, Yaml dumper) {
        // 检查是否是 remap 类型且包含多行 source
        Object typeObj = config.get("type");
        Object sourceObj = config.get("source");

        if ("remap".equals(typeObj) && sourceObj instanceof String) {
            String source = (String) sourceObj;
            if (source.contains("\n")) {
                // 手动构建 YAML，使用字面量块样式
                StringBuilder yaml = new StringBuilder();
                yaml.append("type: remap\n");
                yaml.append("source: |-\n");

                // 添加缩进的源代码（使用 2 空格缩进，与 DumperOptions 一致）
                String[] lines = source.split("\n", -1);  // -1 保留尾部空行
                for (String line : lines) {
                    yaml.append("  ").append(line).append("\n");
                }

                // 处理其他字段（如果有）
                for (Map.Entry<String, Object> entry : config.entrySet()) {
                    String key = entry.getKey();
                    if (!"type".equals(key) && !"source".equals(key)) {
                        // 使用标准 YAML 序列化其他字段
                        Map<String, Object> otherField = new java.util.LinkedHashMap<>();
                        otherField.put(key, entry.getValue());
                        String fieldYaml = dumper.dump(otherField);
                        yaml.append(fieldYaml);
                    }
                }

                return yaml.toString();
            }
        }

        // 对于不包含多行 source 的配置，使用标准 YAML 序列化
        return dumper.dump(config);
    }

    /**
     * 获取 pipeline 名称
     */
    private String getPipelineName(VisualConfig config) {
        if (config.getPipelineName() != null && !config.getPipelineName().isEmpty()) {
            return config.getPipelineName();
        }
        // 使用配置名称生成 pipeline 名称（转换为合法的目录名）
        return config.getName()
                .toLowerCase()
                .replaceAll("[^a-z0-9_-]", "_")
                .replaceAll("_+", "_");
    }

    /**
     * 生成全局配置（包含 API 配置用于状态监控）
     */
    private String generateGlobalConfig() {
        return "# Vector 全局配置\n" +
               "data_dir: \"/opt/vector-agent/data\"\n" +
               "\n" +
               "# 启用 API（用于组件状态监控）\n" +
               "api:\n" +
               "  enabled: true\n" +
               "  address: \"127.0.0.1:8686\"\n";
    }
}
