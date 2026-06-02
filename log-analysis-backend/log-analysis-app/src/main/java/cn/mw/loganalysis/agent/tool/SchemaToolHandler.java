package cn.mw.loganalysis.agent.tool;

import cn.mw.loganalysis.agent.execution.AgentExecutionContext;
import cn.mw.loganalysis.agent.support.AgentToolSupport;
import cn.mw.loganalysis.agent.dto.AgentResult;
import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import cn.mw.loganalysis.stats.service.query.FieldInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SchemaToolHandler {

    private final DynamicLogQueryService dynamicLogQueryService;

    public AgentToolPayload handle(AgentExecutionContext context) {
        long startedAt = System.currentTimeMillis();

        List<FieldInfo> schema = dynamicLogQueryService.getTableSchema(context.datasourceId());
        List<String> timestampFields = schema.stream()
                .filter(field -> Boolean.TRUE.equals(field.getIsTimestamp()))
                .map(FieldInfo::getName)
                .toList();
        List<String> dimensions = schema.stream()
                .filter(field -> Boolean.TRUE.equals(field.getIsStatsDimension()))
                .map(FieldInfo::getName)
                .toList();
        List<String> contentFields = schema.stream()
                .filter(field -> Boolean.TRUE.equals(field.getIsContentField()))
                .map(FieldInfo::getName)
                .toList();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("fieldCount", schema.size());
        summary.put("timestampFields", timestampFields);
        summary.put("statsDimensions", dimensions);
        summary.put("contentFields", contentFields);

        return AgentToolPayload.builder()
                .toolName("get_schema")
                .toolLabel("读取字段结构")
                .intent("schema")
                .summary(String.format("已获取字段结构，共 %d 个字段；时间字段：%s",
                        schema.size(),
                        AgentToolSupport.joinOrFallback(timestampFields, "未识别")))
                .durationMs(System.currentTimeMillis() - startedAt)
                .result(AgentResult.builder()
                        .type("schema")
                        .schema(schema)
                        .summary(summary)
                        .build())
                .build();
    }
}
