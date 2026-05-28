package cn.mw.loganalysis.stats.service;

import cn.mw.loganalysis.stats.dto.AiQueryRequest;
import cn.mw.loganalysis.stats.dto.AiQueryResponse;
import cn.mw.loganalysis.stats.service.query.FieldInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI查询服务
 * 1. 获取数据源表结构
 * 2. 调用Python AI服务生成SQL
 * 3. 使用DynamicLogQueryService执行SQL
 *
 * @author Claude
 * @since 2026-01-22
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiQueryService {

    private final RestTemplate restTemplate;
    private final DynamicLogQueryService dynamicLogQueryService;

    @Value("${ai.service.url:http://localhost:8001}")
    private String aiServiceUrl;

    /**
     * 执行AI查询
     *
     * @param request AI查询请求
     * @return AI查询响应
     */
    public AiQueryResponse query(AiQueryRequest request) {
        long startTime = System.currentTimeMillis();

        log.info("执行AI查询: {}, datasourceId: {}, datasourceIds: {}",
                request.getQuery(), request.getDatasourceId(), request.getDatasourceIds());

        try {
            // 判断是单数据源查询还是多数据源联合查询
            if (request.getDatasourceIds() != null && !request.getDatasourceIds().isEmpty()) {
                // 多数据源联合查询
                return executeMultiDatasourceQuery(request, startTime);
            } else {
                // 单数据源查询（原有逻辑）
                return executeSingleDatasourceQuery(request, startTime);
            }

        } catch (Exception e) {
            log.error("AI查询失败", e);
            return buildErrorResponse("AI查询失败: " + e.getMessage(), startTime);
        }
    }

    /**
     * 只调用 AI service 生成 SQL，不执行 SQL。
     */
    public AiQueryResponse generateSqlOnly(AiQueryRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("生成AI SQL候选: {}, datasourceId: {}", request.getQuery(), request.getDatasourceId());
        try {
            if (request.getDatasourceIds() != null && !request.getDatasourceIds().isEmpty()) {
                return buildErrorResponse("候选竞争暂不支持多数据源 SQL 生成", startTime);
            }
            return generateSingleDatasourceSql(request, startTime);
        } catch (Exception e) {
            log.error("AI SQL候选生成失败", e);
            return buildErrorResponse("SQL生成失败: " + e.getMessage(), startTime);
        }
    }

    /**
     * 执行单数据源查询（原有逻辑）
     */
    private AiQueryResponse executeSingleDatasourceQuery(AiQueryRequest request, long startTime) {
        AiQueryResponse generated = generateSingleDatasourceSql(request, startTime);
        if (!Boolean.TRUE.equals(generated.getSuccess())) {
            return generated;
        }
        String datasourceId = request.getDatasourceId();
        String sql = generated.getSql();
        Double sqlGenerationTime = generated.getSqlGenerationTime();

        log.info("SQL生成成功，耗时: {}秒", sqlGenerationTime);
        log.debug("生成的SQL: {}", sql);

        // 3. 执行SQL查询
        long sqlStartTime = System.currentTimeMillis();
        Object queryResult;

        if (StringUtils.hasText(datasourceId)) {
            // 使用动态数据源执行
            queryResult = dynamicLogQueryService.executeRawSQL(datasourceId, sql);
        } else {
            // 使用默认数据源执行
            queryResult = dynamicLogQueryService.executeRawSQL(null, sql);
        }

        double sqlExecutionTime = (System.currentTimeMillis() - sqlStartTime) / 1000.0;
        double totalTime = (System.currentTimeMillis() - startTime) / 1000.0;

        log.info("SQL执行成功，执行时间: {}秒，总时间: {}秒", sqlExecutionTime, totalTime);

        // 4. 返回结果
        return AiQueryResponse.builder()
                .success(true)
                .sql(sql)
                .result(queryResult)
                .error(null)
                .sqlGenerationTime(sqlGenerationTime)
                .sqlExecutionTime(sqlExecutionTime)
                .totalExecutionTime(totalTime)
                .build();
    }

    /**
     * 单数据源 SQL 生成公共逻辑。
     */
    private AiQueryResponse generateSingleDatasourceSql(AiQueryRequest request, long startTime) {
        String datasourceId = request.getDatasourceId();
        String tableName;
        String datasourceType;
        List<FieldInfo> tableSchema;

        if (StringUtils.hasText(datasourceId)) {
            tableSchema = dynamicLogQueryService.getTableSchema(datasourceId);
            if (tableSchema == null || tableSchema.isEmpty()) {
                return buildErrorResponse("无法获取数据源表结构", startTime);
            }
            tableName = dynamicLogQueryService.getTableName(datasourceId);
            datasourceType = dynamicLogQueryService.getDatasourceType(datasourceId);
            log.info("使用数据源: {}, 表名: {}, 类型: {}", datasourceId, tableName, datasourceType);
        } else {
            tableName = "syslog";
            datasourceType = "clickhouse";
            tableSchema = getDefaultSyslogSchema();
            log.info("使用默认syslog表");
        }

        Map<String, Object> aiResult = requestAiSql(request.getQuery(), tableName, tableSchema, datasourceType);
        if (aiResult == null || !Boolean.TRUE.equals(aiResult.get("success"))) {
            String error = aiResult != null ? (String) aiResult.get("error") : "AI服务返回空结果";
            return buildErrorResponse("SQL生成失败: " + error, startTime);
        }

        return AiQueryResponse.builder()
                .success(true)
                .sql((String) aiResult.get("sql"))
                .error(null)
                .sqlGenerationTime(((Number) aiResult.get("execution_time")).doubleValue())
                .totalExecutionTime((System.currentTimeMillis() - startTime) / 1000.0)
                .build();
    }

    /**
     * 调用 Python AI service 的 /text-to-sql 接口。
     */
    private Map<String, Object> requestAiSql(String query,
                                             String tableName,
                                             List<FieldInfo> tableSchema,
                                             String datasourceType) {
        Map<String, Object> aiRequest = new HashMap<>();
        aiRequest.put("query", query);
        aiRequest.put("table_name", tableName);
        aiRequest.put("table_schema", tableSchema);
        aiRequest.put("datasource_type", datasourceType);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(aiRequest, headers);

        String url = aiServiceUrl + "/text-to-sql";
        log.debug("调用AI服务: {}", url);

        ResponseEntity<Map> aiResponse = restTemplate.postForEntity(url, entity, Map.class);
        return aiResponse.getBody();
    }

    /**
     * 执行多数据源联合查询
     */
    private AiQueryResponse executeMultiDatasourceQuery(AiQueryRequest request, long startTime) {
        List<String> datasourceIds = request.getDatasourceIds();

        // 1. 验证数据源数量
        if (datasourceIds.size() < 2) {
            return buildErrorResponse("联合查询至少需要2个数据源", startTime);
        }

        if (datasourceIds.size() > 10) {
            return buildErrorResponse("联合查询最多支持10个数据源", startTime);
        }

        // 2. 获取所有数据源的配置并验证类型一致性
        String firstDatasourceType = null;
        List<DatasourceInfo> datasourceInfos = new ArrayList<>();

        for (String datasourceId : datasourceIds) {
            try {
                String tableName = dynamicLogQueryService.getTableName(datasourceId);
                String datasourceType = dynamicLogQueryService.getDatasourceType(datasourceId);
                List<FieldInfo> tableSchema = dynamicLogQueryService.getTableSchema(datasourceId);

                if (tableSchema == null || tableSchema.isEmpty()) {
                    return buildErrorResponse("数据源 " + datasourceId + " 无法获取表结构", startTime);
                }

                // 验证类型一致性
                if (firstDatasourceType == null) {
                    firstDatasourceType = datasourceType;
                } else if (!firstDatasourceType.equalsIgnoreCase(datasourceType)) {
                    return buildErrorResponse(
                            String.format("数据源类型不一致：%s 是 %s，但 %s 是 %s。联合查询要求所有数据源类型相同",
                                    datasourceIds.get(0), firstDatasourceType, datasourceId, datasourceType),
                            startTime);
                }

                datasourceInfos.add(new DatasourceInfo(datasourceId, tableName, datasourceType, tableSchema));
                log.info("数据源 {}: 表名={}, 类型={}", datasourceId, tableName, datasourceType);

            } catch (Exception e) {
                return buildErrorResponse("获取数据源 " + datasourceId + " 配置失败: " + e.getMessage(), startTime);
            }
        }

        // 3. 使用第一个数据源的表结构生成SQL（假设所有数据源表结构相同或兼容）
        DatasourceInfo firstDatasource = datasourceInfos.get(0);

        Map<String, Object> aiRequest = new HashMap<>();
        aiRequest.put("query", request.getQuery());
        aiRequest.put("table_name", firstDatasource.tableName);
        aiRequest.put("table_schema", firstDatasource.tableSchema);
        aiRequest.put("datasource_type", firstDatasource.datasourceType);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(aiRequest, headers);

        String url = aiServiceUrl + "/text-to-sql";
        log.debug("调用AI服务生成SQL: {}", url);

        ResponseEntity<Map> aiResponse = restTemplate.postForEntity(url, entity, Map.class);
        Map<String, Object> aiResult = aiResponse.getBody();

        if (aiResult == null || !Boolean.TRUE.equals(aiResult.get("success"))) {
            String error = aiResult != null ? (String) aiResult.get("error") : "AI服务返回空结果";
            return buildErrorResponse("SQL生成失败: " + error, startTime);
        }

        String baseSql = (String) aiResult.get("sql");
        Double sqlGenerationTime = ((Number) aiResult.get("execution_time")).doubleValue();

        log.info("SQL生成成功，耗时: {}秒", sqlGenerationTime);
        log.debug("生成的基础SQL: {}", baseSql);

        // 4. 对每个数据源执行查询并合并结果
        long sqlStartTime = System.currentTimeMillis();
        List<Object> allResults = new ArrayList<>();

        for (DatasourceInfo datasourceInfo : datasourceInfos) {
            try {
                // 替换表名（如果需要）
                String sql = baseSql.replace(firstDatasource.tableName, datasourceInfo.tableName);

                log.debug("执行数据源 {} 的SQL: {}", datasourceInfo.datasourceId, sql);

                Object result = dynamicLogQueryService.executeRawSQL(datasourceInfo.datasourceId, sql);
                allResults.add(result);

                log.info("数据源 {} 查询成功", datasourceInfo.datasourceId);

            } catch (Exception e) {
                log.error("数据源 {} 查询失败: {}", datasourceInfo.datasourceId, e.getMessage());
                // 继续执行其他数据源，不中断
            }
        }

        // 5. 合并结果
        Object mergedResult = mergeQueryResults(allResults);

        double sqlExecutionTime = (System.currentTimeMillis() - sqlStartTime) / 1000.0;
        double totalTime = (System.currentTimeMillis() - startTime) / 1000.0;

        log.info("多数据源查询完成，执行时间: {}秒，总时间: {}秒，成功数据源: {}/{}",
                sqlExecutionTime, totalTime, allResults.size(), datasourceIds.size());

        // 6. 返回结果
        return AiQueryResponse.builder()
                .success(true)
                .sql(baseSql + " (联合查询 " + datasourceIds.size() + " 个数据源)")
                .result(mergedResult)
                .error(null)
                .sqlGenerationTime(sqlGenerationTime)
                .sqlExecutionTime(sqlExecutionTime)
                .totalExecutionTime(totalTime)
                .build();
    }

    /**
     * 合并多个数据源的查询结果
     */
    private Object mergeQueryResults(List<Object> results) {
        if (results.isEmpty()) {
            return new ArrayList<>();
        }

        // 如果只有一个结果，直接返回
        if (results.size() == 1) {
            return results.get(0);
        }

        // 合并列表结果
        List<Object> mergedList = new ArrayList<>();
        for (Object result : results) {
            if (result instanceof List) {
                mergedList.addAll((List<?>) result);
            } else if (result instanceof Map) {
                // 单个对象包装成列表
                mergedList.add(result);
            } else {
                // 其他类型直接添加
                mergedList.add(result);
            }
        }

        return mergedList;
    }

    /**
     * 数据源信息内部类
     */
    private static class DatasourceInfo {
        String datasourceId;
        String tableName;
        String datasourceType;
        List<FieldInfo> tableSchema;

        DatasourceInfo(String datasourceId, String tableName, String datasourceType, List<FieldInfo> tableSchema) {
            this.datasourceId = datasourceId;
            this.tableName = tableName;
            this.datasourceType = datasourceType;
            this.tableSchema = tableSchema;
        }
    }

    /**
     * 构建错误响应
     */
    private AiQueryResponse buildErrorResponse(String error, long startTime) {
        double totalTime = (System.currentTimeMillis() - startTime) / 1000.0;
        return AiQueryResponse.builder()
                .success(false)
                .sql(null)
                .result(null)
                .error(error)
                .totalExecutionTime(totalTime)
                .build();
    }

    /**
     * 获取默认syslog表结构
     */
    private List<FieldInfo> getDefaultSyslogSchema() {
        return List.of(
                FieldInfo.builder().name("id").type("String").label("ID")
                        .isTimestamp(false).isStatsDimension(false).isContentField(false).build(),
                FieldInfo.builder().name("severity").type("String").label("严重级别")
                        .isTimestamp(false).isStatsDimension(true).isContentField(false).build(),
                FieldInfo.builder().name("hostname").type("String").label("主机名")
                        .isTimestamp(false).isStatsDimension(true).isContentField(false).build(),
                FieldInfo.builder().name("appname").type("String").label("应用名称")
                        .isTimestamp(false).isStatsDimension(true).isContentField(false).build(),
                FieldInfo.builder().name("source_type").type("String").label("来源类型")
                        .isTimestamp(false).isStatsDimension(true).isContentField(false).build(),
                FieldInfo.builder().name("message").type("String").label("日志消息")
                        .isTimestamp(false).isStatsDimension(false).isContentField(true).build(),
                FieldInfo.builder().name("timestamp").type("DateTime").label("时间戳")
                        .isTimestamp(true).isStatsDimension(false).isContentField(false).build(),
                FieldInfo.builder().name("facility").type("String").label("设施")
                        .isTimestamp(false).isStatsDimension(false).isContentField(false).build(),
                FieldInfo.builder().name("procid").type("String").label("进程ID")
                        .isTimestamp(false).isStatsDimension(false).isContentField(false).build(),
                FieldInfo.builder().name("source_ip").type("String").label("来源IP")
                        .isTimestamp(false).isStatsDimension(true).isContentField(false).build(),
                FieldInfo.builder().name("raw").type("String").label("原始日志")
                        .isTimestamp(false).isStatsDimension(false).isContentField(true).build()
        );
    }

    /**
     * 测试AI服务连接
     *
     * @return 是否连接成功
     */
    public boolean testConnection() {
        try {
            String url = aiServiceUrl + "/health";
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("AI服务连接测试失败", e);
            return false;
        }
    }
}
