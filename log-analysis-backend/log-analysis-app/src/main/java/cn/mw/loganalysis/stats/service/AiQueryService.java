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

        log.info("执行AI查询: {}, datasourceId: {}", request.getQuery(), request.getDatasourceId());

        try {
            // 1. 获取表结构信息
            String datasourceId = request.getDatasourceId();
            String tableName;
            String datasourceType;
            List<FieldInfo> tableSchema;

            if (StringUtils.hasText(datasourceId)) {
                // 使用指定的数据源
                tableSchema = dynamicLogQueryService.getTableSchema(datasourceId);
                if (tableSchema == null || tableSchema.isEmpty()) {
                    return buildErrorResponse("无法获取数据源表结构", startTime);
                }

                // 从数据源配置中获取表名和类型
                tableName = dynamicLogQueryService.getTableName(datasourceId);
                datasourceType = dynamicLogQueryService.getDatasourceType(datasourceId);

                log.info("使用数据源: {}, 表名: {}, 类型: {}", datasourceId, tableName, datasourceType);
            } else {
                // 使用默认的syslog表
                tableName = "syslog";
                datasourceType = "clickhouse";
                tableSchema = getDefaultSyslogSchema();
                log.info("使用默认syslog表");
            }

            // 2. 调用Python AI服务生成SQL
            Map<String, Object> aiRequest = new HashMap<>();
            aiRequest.put("query", request.getQuery());
            aiRequest.put("table_name", tableName);
            aiRequest.put("table_schema", tableSchema);
            aiRequest.put("datasource_type", datasourceType);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(aiRequest, headers);

            String url = aiServiceUrl + "/text-to-sql";
            log.debug("调用AI服务: {}", url);

            ResponseEntity<Map> aiResponse = restTemplate.postForEntity(url, entity, Map.class);
            Map<String, Object> aiResult = aiResponse.getBody();

            if (aiResult == null || !Boolean.TRUE.equals(aiResult.get("success"))) {
                String error = aiResult != null ? (String) aiResult.get("error") : "AI服务返回空结果";
                return buildErrorResponse("SQL生成失败: " + error, startTime);
            }

            String sql = (String) aiResult.get("sql");
            Double sqlGenerationTime = ((Number) aiResult.get("execution_time")).doubleValue();

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

        } catch (Exception e) {
            log.error("AI查询失败", e);
            return buildErrorResponse("AI查询失败: " + e.getMessage(), startTime);
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
