package cn.mw.loganalysis.agent.vectorplan;

import cn.mw.loganalysis.agent.support.AgentToolSupport;
import cn.mw.loganalysis.agent.dto.AgentChatResponse;
import cn.mw.loganalysis.agent.dto.AgentResult;
import cn.mw.loganalysis.datasource.entity.Datasource;
import cn.mw.loganalysis.datasource.mapper.DatasourceMapper;
import cn.mw.loganalysis.vector.dto.ConfigComponentRequest;
import cn.mw.loganalysis.vector.dto.CreateVisualConfigRequest;
import cn.mw.loganalysis.vector.dto.UpdateVisualConfigRequest;
import cn.mw.loganalysis.vector.dto.VrlExecuteRequest;
import cn.mw.loganalysis.vector.entity.ConfigComponent;
import cn.mw.loganalysis.vector.entity.VisualConfig;
import cn.mw.loganalysis.vector.service.ComponentYamlGeneratorService;
import cn.mw.loganalysis.vector.service.ConfigComponentService;
import cn.mw.loganalysis.vector.service.VisualConfigService;
import cn.mw.loganalysis.wizard.dto.CreateTableRequest;
import cn.mw.loganalysis.wizard.dto.CreateTableResponse;
import cn.mw.loganalysis.wizard.dto.ParseLogResponse;
import cn.mw.loganalysis.wizard.service.SmartWizardService;
import cn.mw.loganalysis.wizard.service.TableManagementService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 确认创建阶段执行器：校验预览计划后再建表、建组件和生成可部署配置。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorComponentCommitService {

    private final DatasourceMapper datasourceMapper;
    private final SmartWizardService smartWizardService;
    private final TableManagementService tableManagementService;
    private final ComponentYamlGeneratorService componentYamlGeneratorService;
    private final ConfigComponentService configComponentService;
    private final VisualConfigService visualConfigService;
    private final ObjectMapper objectMapper;
    private final VectorComponentPlanStore planStore;

    /**
     * 根据用户确认的 planId 执行建表、组件创建和可部署配置生成。
     */
    public AgentChatResponse commit(String planId, Long userId, String sessionId) {
        if (StringUtils.isBlank(planId)) {
            return commitError("缺少计划 ID，无法确认创建", null);
        }
        if (ObjectUtils.isEmpty(userId) || StringUtils.isBlank(sessionId)) {
            return commitError("会话信息不完整，请重新生成预览计划后再确认创建", null);
        }

        VectorComponentPlan plan = planStore.get(planId);
        if (plan == null) {
            return commitError("预览计划不存在或已过期，请重新让助手生成一次", null);
        }
        if (!Objects.equals(userId, plan.userId()) || !StringUtils.equals(sessionId, plan.sessionId())) {
            log.warn("拒绝提交不属于当前用户/会话的 Vector 组件计划, planId={}, userId={}, sessionId={}",
                    planId, userId, sessionId);
            return commitError("预览计划不属于当前会话，请重新生成后再确认创建", plan);
        }

        try {
            Datasource datasource = datasourceMapper.selectById(plan.datasourceId());
            validateClickHouseDatasource(datasource);
            ensureTableDoesNotExist(plan.datasourceId(), plan.tableName());
            ParseLogResponse parseResponse = parseWithRegex(plan.logSample(), plan.regexPattern());
            if (StringUtils.isBlank(plan.ddl())) {
                return commitError("预览计划缺少 DDL，请重新生成后再确认创建", plan);
            }

            CreateTableRequest request = new CreateTableRequest();
            request.setDatasourceId(plan.datasourceId());
            request.setTableName(plan.tableName());
            request.setDdl(plan.ddl());
            request.setVrlScript(StringUtils.defaultIfBlank(plan.vrlScript(), parseResponse.getVrlScript()));
            request.setParseMethod("parse_regex");
            request.setRegexPattern(plan.regexPattern());
            request.setLogSample(plan.logSample());
            request.setParsedFields(toVisualParsedFields(plan.fields()));
            request.setAutoCreateComponents(true);

            CreateTableResponse createResponse = tableManagementService.createTableWithComponents(request);
            AgentResult result = AgentResult.builder()
                    .type("vector_component_commit")
                    .success(Boolean.TRUE.equals(createResponse.getSuccess()))
                    .error(createResponse.getError())
                    .tableName(createResponse.getTableName())
                    .sourceType(plan.sourceType())
                    .sourceConfig(plan.sourceConfig())
                    .remapComponentId(createResponse.getRemapComponentId())
                    .sinkComponentId(createResponse.getSinkComponentId())
                    .datasourceId(plan.datasourceId())
                    .datasourceName(plan.datasourceName())
                    .warnings(plan.warnings())
                    .build();

            if (!Boolean.TRUE.equals(createResponse.getSuccess())) {
                return AgentChatResponse.builder()
                        .success(false)
                        .intent("vector_component_commit")
                        .answer("确认创建失败：" + StringUtils.defaultString(createResponse.getError(), "未知错误"))
                        .error(createResponse.getError())
                        .datasourceId(plan.queryableDatasourceId())
                        .datasourceName(plan.datasourceName())
                        .result(result)
                        .suggestions(List.of("重新生成 Vector 组件计划", "检查 ClickHouse 数据源连接", "换一个表名后重试"))
                        .build();
            }

            List<String> commitWarnings = new ArrayList<>(plan.warnings());
            VectorComponentCommitArtifacts artifacts = createSourceAndVisualConfig(plan, createResponse, commitWarnings);
            result.setSourceComponentId(artifacts.sourceComponentId());
            result.setVisualConfigId(artifacts.visualConfigId());
            result.setVisualConfigName(artifacts.visualConfigName());
            result.setWarnings(commitWarnings);
            result.setDeployment(buildDeploymentSummary(artifacts.visualConfigId(), artifacts.visualConfigName(), commitWarnings));

            planStore.invalidate(plan.planId());
            return AgentChatResponse.builder()
                    .success(true)
                    .intent("vector_component_commit")
                    .answer(String.format("已创建 ClickHouse 表 %s，并创建 Source 组件 %s、Remap 组件 %s、Sink 组件 %s。%s",
                            createResponse.getTableName(),
                            StringUtils.defaultString(artifacts.sourceComponentId(), "未生成"),
                            createResponse.getRemapComponentId(),
                            createResponse.getSinkComponentId(),
                            StringUtils.isNotBlank(artifacts.visualConfigId())
                                    ? "我也生成了可部署配置，请选择 Vector 主机后再部署。"
                                    : "可部署配置生成失败，请先到 Vector 编排中检查组件。"))
                    .datasourceId(createResponse.getSinkComponentId())
                    .datasourceName(plan.datasourceName())
                    .result(result)
                    .suggestions(StringUtils.isNotBlank(artifacts.visualConfigId())
                            ? List.of("现在部署到 Vector 主机", "先查看字段结构", "稍后我自己部署")
                            : List.of("打开 Vector 编排检查配置", "查看字段结构", "继续生成另一个 Vector 组件"))
                    .build();
        } catch (Exception ex) {
            log.error("确认创建 Vector 组件计划失败, planId={}", planId, ex);
            return commitError("确认创建失败：" + ex.getMessage(), plan);
        }
    }

    /**
     * 构建确认创建失败时统一返回给前端的响应。
     */
    private AgentChatResponse commitError(String message, VectorComponentPlan plan) {
        AgentResult result = AgentResult.builder()
                .type("vector_component_commit")
                .success(false)
                .error(message)
                .tableName(plan != null ? plan.tableName() : null)
                .datasourceId(plan != null ? plan.datasourceId() : null)
                .datasourceName(plan != null ? plan.datasourceName() : null)
                .sourceType(plan != null ? plan.sourceType() : null)
                .sourceConfig(plan != null ? plan.sourceConfig() : null)
                .warnings(plan != null ? plan.warnings() : null)
                .build();
        return AgentChatResponse.builder()
                .success(false)
                .intent("vector_component_commit")
                .answer(message)
                .error(message)
                .datasourceId(plan != null ? plan.queryableDatasourceId() : null)
                .datasourceName(plan != null ? plan.datasourceName() : null)
                .result(result)
                .suggestions(List.of("重新生成 Vector 组件计划", "检查日志样本和表名", "查看字段结构"))
                .build();
    }

    /**
     * 校验预览计划绑定的目标数据源仍然是 ClickHouse。
     */
    private void validateClickHouseDatasource(Datasource datasource) {
        if (datasource == null) {
            throw new IllegalArgumentException("ClickHouse 数据源不存在");
        }
        if (!StringUtils.equalsIgnoreCase(datasource.getType(), "clickhouse")) {
            throw new IllegalArgumentException("目标数据源不是 ClickHouse，当前类型为 " + datasource.getType());
        }
    }

    /**
     * 在提交前再次验证正则能解析原始样本。
     */
    private ParseLogResponse parseWithRegex(String logSample, String regexPattern) {
        VrlExecuteRequest request = new VrlExecuteRequest();
        request.setLogSample(logSample);
        request.setParseMethod("parse_regex");
        request.setRegexPattern(regexPattern);

        ParseLogResponse response = smartWizardService.parseLog(request);
        if (response == null || !response.isSuccess()) {
            throw new IllegalArgumentException(response != null && StringUtils.isNotBlank(response.getError())
                    ? response.getError()
                    : "正则解析失败");
        }
        if (CollectionUtils.isEmpty(response.getFields())) {
            throw new IllegalArgumentException("正则解析未提取到字段，请调整日志样本或 regexPattern");
        }
        boolean hasParseError = response.getFields().stream()
                .anyMatch(field -> StringUtils.equalsIgnoreCase(field.getName(), "parse_error"));
        if (hasParseError) {
            throw new IllegalArgumentException("正则未能匹配日志样本，请调整 regexPattern");
        }
        return response;
    }

    /**
     * 将预览字段转换成建表服务需要的可视化字段结构。
     */
    private List<CreateTableRequest.VisualParsedField> toVisualParsedFields(List<FieldPlan> fields) {
        return fields.stream().map(field -> {
            CreateTableRequest.VisualParsedField visualField = new CreateTableRequest.VisualParsedField();
            visualField.setName(field.name());
            visualField.setNewName(field.name());
            visualField.setDeleted(false);
            visualField.setType(field.type());
            visualField.setValue(field.sampleValue());
            visualField.setComment(field.comment());
            return visualField;
        }).toList();
    }

    /**
     * 创建 Source 组件和可部署画布配置，失败时只降级为告警。
     */
    private VectorComponentCommitArtifacts createSourceAndVisualConfig(VectorComponentPlan plan,
                                                                       CreateTableResponse createResponse,
                                                                       List<String> warnings) {
        try {
            ConfigComponent sourceComponent = createSourceComponent(plan);
            String visualConfigName = plan.tableName() + "_pipeline";
            CreateVisualConfigRequest createRequest = new CreateVisualConfigRequest();
            createRequest.setName(visualConfigName);
            createRequest.setDescription("智能助手自动创建 - " + plan.tableName() + " 日志采集解析入库");
            createRequest.setFormat("namespace_yaml");
            VisualConfig visualConfig = visualConfigService.createConfig(createRequest, String.valueOf(plan.userId()));

            String graphData = buildGraphData(plan, sourceComponent, createResponse);
            UpdateVisualConfigRequest updateRequest = new UpdateVisualConfigRequest();
            updateRequest.setGraphData(graphData);
            updateRequest.setNodeCount(3);
            VisualConfig updatedConfig = visualConfigService.updateConfig(visualConfig.getId(), updateRequest);
            return new VectorComponentCommitArtifacts(sourceComponent.getId(), updatedConfig.getId(), updatedConfig.getName());
        } catch (Exception ex) {
            log.warn("智能助手创建可部署 Vector 配置失败, table={}", plan.tableName(), ex);
            warnings.add("Source 组件或可部署配置生成失败：" + ex.getMessage() + "。表和 Remap/Sink 已创建，可在 Vector 管理中手动编排。");
            return new VectorComponentCommitArtifacts(null, null, null);
        }
    }

    /**
     * 根据预览计划创建具体 Source 组件。
     */
    private ConfigComponent createSourceComponent(VectorComponentPlan plan) throws JsonProcessingException {
        ConfigComponentRequest request = new ConfigComponentRequest();
        request.setName(plan.tableName() + "_" + plan.sourceType() + "_source");
        request.setComponentType("source");
        request.setVectorType(plan.sourceType());
        request.setDescription("智能助手自动创建 - " + plan.tableName() + " 日志来源");
        request.setIsTemplate(false);
        request.setVisualData(objectMapper.writeValueAsString(plan.sourceConfig()));
        request.setConfigYaml(componentYamlGeneratorService.generateYaml(
                "source",
                plan.sourceType(),
                request.getVisualData()
        ));
        return configComponentService.create(request, "agent");
    }

    /**
     * 组装 Source -> Remap -> Sink 的可视化编排图数据。
     */
    private String buildGraphData(VectorComponentPlan plan,
                                  ConfigComponent sourceComponent,
                                  CreateTableResponse createResponse) throws JsonProcessingException {
        String sourceNodeId = plan.tableName() + "_source";
        String processorNodeId = plan.tableName() + "_processors";
        String remapStepId = plan.tableName() + "_remap";
        String sinkNodeId = plan.tableName() + "_sink";

        Map<String, Object> sourceNode = new LinkedHashMap<>();
        sourceNode.put("id", sourceNodeId);
        sourceNode.put("type", "source");
        sourceNode.put("position", Map.of("x", 80, "y", 120));
        sourceNode.put("data", Map.of(
                "category", "source",
                "componentType", plan.sourceType() + "_source",
                "componentId", sourceComponent.getId(),
                "name", sourceComponent.getName(),
                "config", plan.sourceConfig(),
                "isShared", false,
                "referenceCount", 0
        ));

        Map<String, Object> remapStep = new LinkedHashMap<>();
        remapStep.put("id", remapStepId);
        remapStep.put("type", "remap");
        remapStep.put("label", plan.tableName() + "_remap");
        remapStep.put("componentId", createResponse.getRemapComponentId());
        remapStep.put("config", Map.of());

        Map<String, Object> processorNode = new LinkedHashMap<>();
        processorNode.put("id", processorNodeId);
        processorNode.put("type", "processors");
        processorNode.put("position", Map.of("x", 380, "y", 120));
        processorNode.put("data", Map.of(
                "category", "transform",
                "name", plan.tableName() + "_processors",
                "isProcessorsContainer", true,
                "steps", List.of(remapStep),
                "componentType", "processors_transform"
        ));

        Map<String, Object> sinkNode = new LinkedHashMap<>();
        sinkNode.put("id", sinkNodeId);
        sinkNode.put("type", "sink");
        sinkNode.put("position", Map.of("x", 680, "y", 120));
        sinkNode.put("data", Map.of(
                "category", "sink",
                "componentType", "clickhouse_sink",
                "componentId", createResponse.getSinkComponentId(),
                "name", plan.tableName() + "_sink",
                "config", Map.of(),
                "isShared", false,
                "referenceCount", 0
        ));

        List<Map<String, Object>> edges = List.of(
                graphEdge("e-" + sourceNodeId + "-" + processorNodeId, sourceNodeId, processorNodeId),
                graphEdge("e-" + processorNodeId + "-" + sinkNodeId, processorNodeId, sinkNodeId)
        );
        return objectMapper.writeValueAsString(Map.of(
                "nodes", List.of(sourceNode, processorNode, sinkNode),
                "edges", edges
        ));
    }

    /**
     * 构建可视化编排图中的连线。
     */
    private Map<String, Object> graphEdge(String id, String source, String target) {
        Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("id", id);
        edge.put("source", source);
        edge.put("target", target);
        edge.put("type", "smoothstep");
        return edge;
    }

    /**
     * 构建确认创建后提示前端继续选择 Vector 主机部署的信息。
     */
    private Map<String, Object> buildDeploymentSummary(String visualConfigId,
                                                       String visualConfigName,
                                                       List<String> warnings) {
        Map<String, Object> deployment = new LinkedHashMap<>();
        deployment.put("ready", StringUtils.isNotBlank(visualConfigId));
        deployment.put("visualConfigId", visualConfigId);
        deployment.put("visualConfigName", visualConfigName);
        deployment.put("nextAction", StringUtils.isNotBlank(visualConfigId)
                ? "请选择要部署的在线 Vector 主机，然后点击开始部署。"
                : "请先到 Vector 编排中检查并保存可部署配置。");
        deployment.put("requiresHostSelection", true);
        deployment.put("warnings", warnings);
        return deployment;
    }

    /**
     * 提交前检查目标表名是否已经存在。
     */
    private void ensureTableDoesNotExist(String datasourceId, String tableName) {
        List<Map<String, Object>> tables = tableManagementService.listTables(datasourceId);
        boolean exists = tables.stream()
                .map(table -> AgentToolSupport.stringify(table.get("name")))
                .anyMatch(name -> StringUtils.equalsIgnoreCase(name, tableName));
        if (exists) {
            throw new IllegalArgumentException("目标表已存在，请更换表名后重新生成计划：" + tableName);
        }
    }
}
