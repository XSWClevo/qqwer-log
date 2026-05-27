package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.dto.AgentResult;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CREATE_LOG_PARSER 补槽阶段的展示组装器。
 *
 * 后端只返回结构化含义和下一步动作，前端负责把它渲染成可交互卡片。
 */
@Component
@RequiredArgsConstructor
public class CreateLogParserRequirementPresenter {

    private static final String DEFAULT_PARSE_METHOD = "parse_regex";

    private final CreateLogParserSlotPolicy slotPolicy;

    /**
     * 构建补槽阶段的工具载荷和前端 requirements 卡片数据。
     */
    AgentToolPayload build(AgentExecutionContext context, AgentTaskFrame frame, long durationMs) {
        List<String> missingSlots = ObjectUtils.defaultIfNull(frame.getMissingSlots(), List.of());

        Map<String, Object> filledSlots = new LinkedHashMap<>();
        putIfNotBlank(filledSlots, "logSample", frame.getLogSample());
        putIfNotBlank(filledSlots, "targetSinkId", frame.getTargetSinkId());
        putIfNotBlank(filledSlots, "targetDatasourceId", frame.getTargetDatasourceId());
        putIfNotBlank(filledSlots, "tableName", frame.getTableName());
        putIfNotBlank(filledSlots, "componentPrefix", frame.getComponentPrefix());
        putIfNotBlank(filledSlots, "sourceType", frame.getSourceType());
        if (frame.getSourceConfig() != null && !frame.getSourceConfig().isEmpty()) {
            filledSlots.put("sourceConfig", frame.getSourceConfig());
        }
        putIfNotBlank(filledSlots, "regexPattern", frame.getRegexPattern());
        filledSlots.put("parseMethod", StringUtils.defaultIfBlank(frame.getParseMethod(), DEFAULT_PARSE_METHOD));

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("taskId", frame.getTaskId());
        summary.put("status", frame.getStatus().name());
        summary.put("filledSlots", filledSlots);
        summary.put("missingSlots", missingSlots);
        summary.put("sourceTypeOptions", slotPolicy.buildSourceTypeOptions());
        summary.put("sourceFields", slotPolicy.buildSourceFields(frame.getSourceType()));
        summary.put("sourceConfig", ObjectUtils.defaultIfNull(frame.getSourceConfig(), Map.of()));
        summary.put("examples", buildRequirementExamples(frame));
        summary.put("cardMeaning", "这张卡表示助手已经识别出创建日志解析任务，但仍在等待你补齐创建 Source、入库和部署前所需的信息；补齐前不会建表、不会写组件、不会部署。");
        summary.put("workflow", List.of("补齐日志来源和入库目标", "生成 Source/Remap/Sink 预览", "确认后创建组件", "选择 Vector 主机再部署"));
        summary.put("nextAction", frame.getNextAction());

        List<String> warnings = buildWarnings(frame, missingSlots);

        AgentResult result = AgentResult.builder()
                .type("vector_component_requirements")
                .success(true)
                .logSample(frame.getLogSample())
                .datasourceId(frame.getTargetDatasourceId())
                .datasourceName(frame.getDatasourceName())
                .tableName(frame.getTableName())
                .summary(summary)
                .warnings(warnings)
                .build();

        return AgentToolPayload.builder()
                .toolName("collect_vector_component_requirements")
                .toolLabel("补齐 Vector 创建信息")
                .intent("vector_component_requirements")
                .summary(buildRequirementAnswer(context, frame, missingSlots))
                .durationMs(durationMs)
                .result(result)
                .build();
    }

    /**
     * 根据缺失槽位构建补槽阶段的提示告警。
     */
    private List<String> buildWarnings(AgentTaskFrame frame, List<String> missingSlots) {
        List<String> warnings = new ArrayList<>();
        if (missingSlots.contains("targetDatasource")) {
            warnings.add("需要确认目标 ClickHouse Sink/数据源后才能生成入库计划。");
        }
        if (StringUtils.isNotBlank(frame.getTargetSinkId())) {
            warnings.add("已识别当前选中的 ClickHouse Sink，预览前仍会作为目标写入位置展示给你确认。");
        }
        if (missingSlots.stream().anyMatch(slot -> slot.startsWith("source."))) {
            warnings.add("已选择日志来源类型，但还需要补齐该来源的连接参数，例如文件路径、监听地址端口或 Kafka topic。");
        }
        return warnings;
    }

    /**
     * 构建助手对用户的补槽追问文案。
     */
    private String buildRequirementAnswer(AgentExecutionContext context, AgentTaskFrame frame, List<String> missingSlots) {
        List<String> questions = new ArrayList<>();
        if (missingSlots.contains("targetDatasource")) {
            questions.add("目标存到哪个 ClickHouse Sink/数据源");
        }
        if (missingSlots.contains("tableName")) {
            questions.add("目标表名或是否允许自动生成表名");
        }
        if (missingSlots.contains("sourceType")) {
            questions.add("日志来源是 file、syslog、socket，还是 kafka");
        }
        questions.addAll(missingSlots.stream()
                .filter(slot -> slot.startsWith("source."))
                .map(slotPolicy::formatMissingSourceSlot)
                .toList());
        if (missingSlots.contains("logSample")) {
            questions.add("一条原始日志样本");
        }

        StringBuilder answer = new StringBuilder("我理解你要基于日志样本创建解析规则和 Vector 组件计划。");
        if (StringUtils.isNotBlank(frame.getLogSample())) {
            answer.append("我已识别日志样本：").append(frame.getLogSample()).append("。");
        }
        if (StringUtils.isNotBlank(frame.getDatasourceName())) {
            answer.append("目标候选为当前选中的 ").append(frame.getDatasourceName()).append("。");
        } else if (context != null && StringUtils.isNotBlank(context.datasourceName())) {
            answer.append("当前选择的数据源不是可用的 ClickHouse Sink，需要重新确认目标。");
        }
        answer.append("还需要确认：").append(String.join("、", questions)).append("。");
        answer.append("确认后我会先生成正则、VRL、字段、DDL 和 Source/Remap/Sink 编排预览，不会直接建表、写组件或部署。");
        return answer.toString();
    }

    /**
     * 构建前端快捷回复示例。
     */
    private List<String> buildRequirementExamples(AgentTaskFrame frame) {
        String tableName = StringUtils.defaultIfBlank(frame.getTableName(), "app_text_to_sql_logs");
        return List.of(
                "存当前数据源，表名 " + tableName + "，来源 file，路径 /var/log/app/*.log",
                "存当前数据源，表名 " + tableName + "，来源 syslog，udp 0.0.0.0:514",
                "存当前数据源，表名 " + tableName + "，来源 socket，tcp 0.0.0.0:9000",
                "存当前数据源，表名 " + tableName + "，来源 kafka，bootstrap localhost:9092，topic app-logs"
        );
    }

    /**
     * 将非空字段写入展示用 Map。
     */
    private void putIfNotBlank(Map<String, Object> target, String key, Object value) {
        if (ObjectUtils.isNotEmpty(value) && StringUtils.isNotBlank(String.valueOf(value))) {
            target.put(key, value);
        }
    }
}
