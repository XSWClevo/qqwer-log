package cn.mw.loganalysis.vector.service;

import cn.mw.loganalysis.logsource.entity.TrustedLogSource;
import cn.mw.loganalysis.logsource.repository.TrustedLogSourceRepository;
import cn.mw.loganalysis.vector.entity.VisualConfig;
import cn.mw.loganalysis.vector.mapper.MachineConfigMapper;
import cn.mw.loganalysis.vector.mapper.VisualConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final TrustedLogSourceRepository trustedLogSourceRepository;

    @Value("${CLICKHOUSE_HOST:localhost}")
    private String clickhouseHost;

    @Value("${CLICKHOUSE_PORT:8123}")
    private String clickhousePort;

    @Value("${CLICKHOUSE_DB:default}")
    private String clickhouseDatabase;

    @Value("${CLICKHOUSE_USER:default}")
    private String clickhouseUser;

    @Value("${CLICKHOUSE_PASSWORD:12345678}")
    private String clickhousePassword;

    @Value("${LOG_ANALYSIS_BACKEND_URL:http://localhost:8080}")
    private String defaultBackendUrl;

    /**
     * 为指定机器生成完整的 config-dir 结构
     * 
     * @param machineId 机器ID
     * @return Map<文件路径, 文件内容>
     */
    public Map<String, String> generateConfigDir(String machineId) {
        return generateConfigDir(machineId, defaultBackendUrl);
    }

    public Map<String, String> generateConfigDir(String machineId, String notificationBaseUrl) {
        Map<String, String> configFiles = new LinkedHashMap<>();
        
        // 1. 获取该机器已部署的所有配置
        List<VisualConfig> deployedConfigs = machineConfigMapper.selectDeployedConfigsByMachineId(machineId);
        
        log.info("机器 {} 已部署的配置数量: {}", machineId, deployedConfigs.size());
        for (VisualConfig config : deployedConfigs) {
            log.info("  - 配置: id={}, name={}, contentLength={}", 
                    config.getId(), config.getName(), 
                    config.getContent() != null ? config.getContent().length() : 0);
        }
        
        // 2. 添加全局配置（包含 API 配置用于状态监控）— 始终生成
        configFiles.put("global.yaml", generateGlobalConfig());
        
        // 3. 添加 Vector 自身运行日志采集 pipeline — 始终生成，传入 machineId 用于日志归属
        configFiles.putAll(generateInternalLogsPipeline(machineId));
        
        if (deployedConfigs.isEmpty()) {
            log.warn("机器 {} 没有已部署的用户配置，仅生成全局配置和内部日志 pipeline", machineId);
            return configFiles;
        }
        
        // 4. 为每个配置生成独立的组件文件
        for (VisualConfig config : deployedConfigs) {
            log.info("处理配置: {}, content:\n{}", config.getName(), config.getContent());
            generatePipelineFiles(configFiles, config.getName(), config.getContent(), notificationBaseUrl);
        }
        
        log.info("为机器 {} 生成 {} 个配置文件: {}", machineId, configFiles.size(), configFiles.keySet());
        return configFiles;
    }

    /**
     * 为单个配置生成 pipeline 目录结构
     * 
     * @param machineId 机器ID
     * @param configId  配置ID
     * @return Map<文件路径, 文件内容>
     */
    public Map<String, String> generatePipelineConfig(String machineId, String configId) {
        Map<String, String> configFiles = new LinkedHashMap<>();
        
        VisualConfig config = visualConfigMapper.selectById(configId);
        if (config == null) {
            throw new RuntimeException("配置不存在: " + configId);
        }
        
        // 添加全局配置（包含 API 配置）
        configFiles.put("global.yaml", generateGlobalConfig());

        // 添加 Vector 自身运行日志采集 pipeline
        configFiles.putAll(generateInternalLogsPipeline(machineId));
        
        // 生成 pipeline 文件
        String pipelineName = getPipelineName(config);
        generatePipelineFiles(configFiles, pipelineName, config.getContent(), defaultBackendUrl);
        
        return configFiles;
    }

    /**
     * 直接从配置内容生成 config-dir 结构
     * 不依赖数据库状态，用于部署时生成配置
     * 
     * @param machineId   机器ID
     * @param configId    配置ID（用于获取 pipeline 名称）
     * @param yamlContent 配置内容
     * @return Map<文件路径, 文件内容>
     */
    public Map<String, String> generateConfigDirFromContent(String machineId, String configId, String yamlContent) {
        Map<String, String> configFiles = new LinkedHashMap<>();
        
        if (yamlContent == null || yamlContent.trim().isEmpty()) {
            log.warn("配置内容为空: {}", configId);
            return configFiles;
        }
        
        // 添加全局配置（包含 API 配置）
        configFiles.put("global.yaml", generateGlobalConfig());

        // 添加 Vector 自身运行日志采集 pipeline
        configFiles.putAll(generateInternalLogsPipeline(machineId));
        
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
        generatePipelineFiles(configFiles, pipelineName, yamlContent, defaultBackendUrl);
        
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
    private void generatePipelineFiles(Map<String, String> configFiles, String pipelineName, String yamlContent, String notificationBaseUrl) {
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

        applyLogSourcePolicy(parsed, notificationBaseUrl);
        normalizeSensitiveStringFields(parsed);
        
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        Yaml dumper = new Yaml(new PasswordQuotingRepresenter(options));
        
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
     * 只对 syslog/socket source 注入日志源白名单策略。其他 source 不改输入，直接通行。
     */
    @SuppressWarnings("unchecked")
    private void applyLogSourcePolicy(Map<String, Object> parsed, String notificationBaseUrl) {
        Map<String, Object> sources = asObjectMap(parsed.get("sources"));
        if (MapUtils.isEmpty(sources)) {
            return;
        }

        List<String> managedSources = sources.entrySet().stream()
                .filter(entry -> isManagedLogSource(entry.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(managedSources)) {
            return;
        }

        Map<String, Object> transforms = getOrCreateSection(parsed, "transforms");
        Map<String, Object> sinks = getOrCreateSection(parsed, "sinks");
        Set<String> originalTransformNames = new LinkedHashSet<>(transforms.keySet());
        Set<String> originalSinkNames = new LinkedHashSet<>(sinks.keySet());

        List<String> trustedIps = getSourceIps(trustedLogSourceRepository.findTrustedSources());
        List<String> suppressedIps = getSourceIps(Stream.concat(
                        trustedLogSourceRepository.findPendingSources().stream(),
                        trustedLogSourceRepository.findBlockedSources().stream())
                .collect(Collectors.toList()));

        for (String sourceName : managedSources) {
            String normalizedName = sourceName + "_mw_log_source_normalize";
            String allowedName = sourceName + "_mw_log_source_allowed";
            String unknownName = sourceName + "_mw_log_source_unknown";
            String notifyPayloadName = sourceName + "_mw_log_source_notify_payload";
            String notifySinkName = sourceName + "_mw_log_source_notify";

            rewriteOriginalInputs(transforms, originalTransformNames, sourceName, allowedName);
            rewriteOriginalInputs(sinks, originalSinkNames, sourceName, allowedName);

            transforms.put(normalizedName, remap(List.of(sourceName), generateNormalizeSource()));
            transforms.put(allowedName, filter(List.of(normalizedName), generateAllowedCondition(trustedIps)));
            transforms.put(unknownName, filter(List.of(normalizedName), generateUnknownCondition(trustedIps, suppressedIps)));
            transforms.put(notifyPayloadName, remap(List.of(unknownName), generateNotifyPayloadSource()));
            sinks.put(notifySinkName, httpNotifySink(List.of(notifyPayloadName), notificationBaseUrl));
        }

        log.info("已为 pipeline 注入日志源白名单策略: sources={}, trusted={}, suppressed={}",
                managedSources, trustedIps.size(), suppressedIps.size());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() != null) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getOrCreateSection(Map<String, Object> parsed, String section) {
        Object existing = parsed.get(section);
        if (existing instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    result.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            parsed.put(section, result);
            return result;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        parsed.put(section, result);
        return result;
    }

    private boolean isManagedLogSource(Object componentConfig) {
        Map<String, Object> config = asObjectMap(componentConfig);
        if (MapUtils.isEmpty(config)) {
            return false;
        }

        String type = String.valueOf(config.get("type"));
        return StringUtils.equals(type, "syslog") || StringUtils.equals(type, "socket");
    }

    @SuppressWarnings("unchecked")
    private void rewriteOriginalInputs(Map<String, Object> components, Set<String> originalNames,
                                       String sourceName, String replacementName) {
        for (String componentName : originalNames) {
            Object componentConfig = components.get(componentName);
            if (!(componentConfig instanceof Map<?, ?> map)) {
                continue;
            }

            Object inputsObj = map.get("inputs");
            if (!(inputsObj instanceof List<?> inputs)) {
                continue;
            }

            List<Object> rewritten = inputs.stream()
                    .map(input -> StringUtils.equals(String.valueOf(input), sourceName) ? replacementName : input)
                    .collect(Collectors.toList());
            ((Map<String, Object>) componentConfig).put("inputs", rewritten);
        }
    }

    private Map<String, Object> remap(List<String> inputs, String source) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("type", "remap");
        config.put("inputs", inputs);
        config.put("source", source);
        return config;
    }

    private Map<String, Object> filter(List<String> inputs, String condition) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("type", "filter");
        config.put("inputs", inputs);
        config.put("condition", condition);
        return config;
    }

    private Map<String, Object> httpNotifySink(List<String> inputs, String notificationBaseUrl) {
        Map<String, Object> sink = new LinkedHashMap<>();
        sink.put("type", "http");
        sink.put("inputs", inputs);
        sink.put("uri", normalizeBaseUrl(notificationBaseUrl) + "/api/log-sources/notify-new-ip");
        sink.put("method", "post");
        sink.put("encoding", Map.of("codec", "json"));
        sink.put("request", Map.of("headers", Map.of("Content-Type", "application/json")));
        sink.put("batch", Map.of("max_events", 10, "timeout_secs", 30));
        return sink;
    }

    private String normalizeBaseUrl(String notificationBaseUrl) {
        String baseUrl = StringUtils.defaultIfBlank(notificationBaseUrl, defaultBackendUrl);
        return StringUtils.removeEnd(baseUrl, "/");
    }

    private String generateNormalizeSource() {
        return """
                raw_source_ip = to_string(.source_ip) ?? to_string(.host) ?? "unknown"
                .source_ip = replace(raw_source_ip, r':\\d+$', "")
                if .source_ip == "" {
                  .source_ip = "unknown"
                }
                """;
    }

    private String generateNotifyPayloadSource() {
        return """
                . = {
                  "notificationType": "new_log_source",
                  "sourceIp": to_string(.source_ip) ?? "unknown",
                  "hostname": to_string(.hostname) ?? to_string(.host) ?? "unknown",
                  "logCount": 1
                }
                """;
    }

    private String generateAllowedCondition(List<String> trustedIps) {
        return ".source_ip != \"unknown\" && includes(" + toVrlArray(trustedIps) + ", to_string(.source_ip) ?? \"\")";
    }

    private String generateUnknownCondition(List<String> trustedIps, List<String> suppressedIps) {
        return ".source_ip != \"unknown\""
                + " && !includes(" + toVrlArray(trustedIps) + ", to_string(.source_ip) ?? \"\")"
                + " && !includes(" + toVrlArray(suppressedIps) + ", to_string(.source_ip) ?? \"\")";
    }

    private List<String> getSourceIps(List<TrustedLogSource> sources) {
        if (CollectionUtils.isEmpty(sources)) {
            return List.of();
        }

        return sources.stream()
                .map(TrustedLogSource::getSourceIp)
                .map(StringUtils::trimToEmpty)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
    }

    private String toVrlArray(List<String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return "[]";
        }

        return values.stream()
                .map(value -> "\"" + escapeVrlString(value) + "\"")
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private String escapeVrlString(String value) {
        return StringUtils.defaultString(value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    @SuppressWarnings("unchecked")
    private void normalizeSensitiveStringFields(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object key = entry.getKey();
                Object item = entry.getValue();
                if (key != null && StringUtils.equals("password", String.valueOf(key)) && item != null) {
                    ((Map<Object, Object>) map).put(key, String.valueOf(item));
                } else {
                    normalizeSensitiveStringFields(item);
                }
            }
            return;
        }

        if (value instanceof List<?> list) {
            for (Object item : list) {
                normalizeSensitiveStringFields(item);
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

    /**
     * 生成 Vector 运行日志采集 pipeline 的配置文件集合。
     * file source 读取 /opt/vector-agent/logs/*.log → remap 提取文件名 → clickhouse sink 写入 vector_logs 表
     *
     * @param machineId 机器ID（UUID），写入日志的 machine_id 字段，用于关联机器
     */
    private Map<String, String> generateInternalLogsPipeline(String machineId) {
        Map<String, String> files = new LinkedHashMap<>();

        // 1. file source：读取 /opt/vector-agent/logs/ 下所有 .log 文件
        String sourceYaml =
                "type: file\n" +
                "include:\n" +
                "  - /opt/vector-agent/logs/*.log\n" +
                "read_from: end\n" +
                "fingerprint:\n" +
                "  strategy: device_and_inode\n";
        files.put("sources/_vector_file_logs.yaml", sourceYaml);

        // 2. remap transform：提取文件名，设置 machine_id 和时间戳
        String remapVrl =
                "type: remap\n" +
                "inputs:\n" +
                "  - _vector_file_logs\n" +
                "source: |-\n" +
                "  .file_name = replace(to_string(.file) ?? \"unknown\", \"/opt/vector-agent/logs/\", \"\")\n" +
                "  .machine_id = \"" + machineId + "\"\n" +
                "  .timestamp = format_timestamp!(now(), format: \"%Y-%m-%d %H:%M:%S\", timezone: \"Asia/Shanghai\")\n";
        files.put("transforms/_vector_log_remap.yaml", remapVrl);

        // 3. clickhouse sink：写入 vector_logs 表
        String sinkYaml =
                "type: clickhouse\n" +
                "inputs:\n" +
                "  - _vector_log_remap\n" +
                "endpoint: http://" + clickhouseHost + ":" + clickhousePort + "\n" +
                "database: " + clickhouseDatabase + "\n" +
                "table: vector_logs\n" +
                "skip_unknown_fields: true\n" +
                "encoding:\n" +
                "  timestamp_format: rfc3339\n" +
                "auth:\n" +
                "  strategy: basic\n" +
                "  user: " + clickhouseUser + "\n" +
                "  password: \"" + clickhousePassword + "\"\n" +
                "batch:\n" +
                "  max_bytes: 5000000\n" +
                "  timeout_secs: 15\n" +
                "buffer:\n" +
                "  type: memory\n" +
                "  max_events: 10000\n";
        files.put("sinks/_vector_log_sink.yaml", sinkYaml);

        return files;
    }
}
