package cn.mw.loganalysis.agent.vectorplan;

import java.util.List;
import java.util.Map;

public record VectorComponentPlan(String planId,
                           Long userId,
                           String sessionId,
                           String queryableDatasourceId,
                           String datasourceId,
                           String datasourceName,
                           String tableName,
                           String logSample,
                           String regexPattern,
                           String vrlScript,
                           String sourceType,
                           Map<String, Object> sourceConfig,
                           List<FieldPlan> fields,
                           String ddl,
                           List<String> warnings) {
}
