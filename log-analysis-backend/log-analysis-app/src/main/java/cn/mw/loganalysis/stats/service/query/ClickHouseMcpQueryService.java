package cn.mw.loganalysis.stats.service.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通过官方 mcp-clickhouse Server 执行只读查询。
 *
 * 当前实现使用官方 Java SDK 的 STDIO Client，而不是继续手写 JSON-RPC 协议。
 * 这样保留了“按当前数据源动态起一个 mcp-clickhouse 进程”的灵活性，同时把协议细节交回 SDK。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClickHouseMcpQueryService {

    private static final String CLIENT_NAME = "log-analysis-backend";
    private static final String CLIENT_VERSION = "1.0.0";

    private final ClickHouseMcpProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * 官方 SDK 通过 McpJsonMapper 处理协议层序列化。
     * 这里直接复用 SDK 默认的 Jackson 实现，避免自己再维护一套 JSON-RPC 编解码。
     */
    private final McpJsonMapper mcpJsonMapper = McpJsonMapper.getDefault();

    public boolean shouldUse(DatasourceConnectionConfig config) {
        return properties.isEnabled()
                && properties.hasExecutable()
                && config != null
                && "clickhouse".equalsIgnoreCase(config.getType());
    }

    public boolean isFallbackToJdbcOnError() {
        return properties.isFallbackToJdbcOnError();
    }

    public List<Map<String, Object>> executeSelect(String renderedSql, DatasourceConnectionConfig config) {
        if (!StringUtils.hasText(renderedSql)) {
            return List.of();
        }
        try (McpClientSession session = openSession(config)) {
            ToolDescriptor queryTool = resolveQueryTool(session.client());
            McpSchema.CallToolResult toolResult = session.client()
                    .callTool(new McpSchema.CallToolRequest(queryTool.name(), Map.of(queryTool.argumentName(), renderedSql)));
            if (Boolean.TRUE.equals(toolResult.isError())) {
                throw new IllegalStateException(extractErrorMessage(toolResult));
            }
            return extractRows(toolResult, renderedSql);
        } catch (IOException ex) {
            throw new IllegalStateException("调用 ClickHouse MCP 失败: " + ex.getMessage(), ex);
        }
    }

    public Long executeCount(String renderedSql, DatasourceConnectionConfig config) {
        List<Map<String, Object>> rows = executeSelect(renderedSql, config);
        if (rows.isEmpty()) {
            return 0L;
        }
        Map<String, Object> firstRow = rows.get(0);
        if (firstRow.isEmpty()) {
            return 0L;
        }
        Object firstValue = firstRow.values().iterator().next();
        if (firstValue instanceof Number number) {
            return number.longValue();
        }
        if (!StringUtils.hasText(String.valueOf(firstValue))) {
            return 0L;
        }
        return Long.parseLong(String.valueOf(firstValue));
    }

    private McpClientSession openSession(DatasourceConnectionConfig config) throws IOException {
        StdioClientTransport transport = new StdioClientTransport(buildServerParameters(config), mcpJsonMapper);
        transport.setStdErrorHandler(line -> {
            if (StringUtils.hasText(line)) {
                log.debug("mcp-clickhouse stderr: {}", line);
            }
        });

        McpSyncClient client = null;
        try {
            client = McpClient.sync(transport)
                    .requestTimeout(properties.getRequestTimeout())
                    .initializationTimeout(properties.getStartupTimeout())
                    .clientInfo(new McpSchema.Implementation(CLIENT_NAME, CLIENT_VERSION))
                    .build();

            McpSchema.InitializeResult initializeResult = client.initialize();
            if (initializeResult != null && initializeResult.serverInfo() != null) {
                log.debug("ClickHouse MCP initialized: server={}, version={}, protocol={}",
                        initializeResult.serverInfo().name(),
                        initializeResult.serverInfo().version(),
                        initializeResult.protocolVersion());
            }

            return new McpClientSession(client);
        } catch (RuntimeException ex) {
            closeQuietly(client);
            throw new IllegalStateException("初始化 ClickHouse MCP 失败: " + ex.getMessage(), ex);
        }
    }

    private ServerParameters buildServerParameters(DatasourceConnectionConfig config) {
        ServerParameters.Builder builder = ServerParameters.builder(properties.getExecutable())
                .args(properties.getArguments())
                .env(buildClickHouseEnv(config));
        return builder.build();
    }

    private Map<String, String> buildClickHouseEnv(DatasourceConnectionConfig config) {
        Endpoint endpoint = parseEndpoint(config);
        Map<String, String> env = new LinkedHashMap<>();
        env.put("CLICKHOUSE_MCP_SERVER_TRANSPORT", "stdio");
        env.put("CLICKHOUSE_HOST", endpoint.host());
        env.put("CLICKHOUSE_PORT", String.valueOf(endpoint.port()));
        env.put("CLICKHOUSE_USER", StringUtils.hasText(config.getUsername()) ? config.getUsername() : "default");
        env.put("CLICKHOUSE_PASSWORD", config.getPassword() != null ? config.getPassword() : "");
        env.put("CLICKHOUSE_DATABASE", StringUtils.hasText(config.getDatabase()) ? config.getDatabase() : "default");
        env.put("CLICKHOUSE_SECURE", String.valueOf(endpoint.secure()));
        env.put("CLICKHOUSE_VERIFY", String.valueOf(properties.isVerifySsl()));
        env.put("CLICKHOUSE_CONNECT_TIMEOUT", String.valueOf(properties.getConnectTimeoutSeconds()));
        env.put("CLICKHOUSE_SEND_RECEIVE_TIMEOUT", String.valueOf(properties.getSendReceiveTimeoutSeconds()));
        return env;
    }

    private Endpoint parseEndpoint(DatasourceConnectionConfig config) {
        String rawEndpoint = config.getEndpoint();
        if (!StringUtils.hasText(rawEndpoint)) {
            return new Endpoint("localhost", 8123, Boolean.TRUE.equals(config.getTls()));
        }

        String normalized = rawEndpoint.contains("://") ? rawEndpoint : "http://" + rawEndpoint;
        URI uri = URI.create(normalized);
        String host = StringUtils.hasText(uri.getHost()) ? uri.getHost() : rawEndpoint;
        int port = uri.getPort() > 0 ? uri.getPort() : 8123;
        boolean secure = Boolean.TRUE.equals(config.getTls()) || "https".equalsIgnoreCase(uri.getScheme());
        return new Endpoint(host, port, secure);
    }

    private ToolDescriptor resolveQueryTool(McpSyncClient client) {
        String cursor = null;
        do {
            McpSchema.ListToolsResult result = StringUtils.hasText(cursor) ? client.listTools(cursor) : client.listTools();
            if (result != null && result.tools() != null) {
                for (String candidate : List.of("run_select_query", "run_query")) {
                    for (McpSchema.Tool tool : result.tools()) {
                        if (candidate.equals(tool.name())) {
                            return new ToolDescriptor(candidate, resolveQueryArgumentName(tool));
                        }
                    }
                }
                cursor = result.nextCursor();
            } else {
                cursor = null;
            }
        } while (StringUtils.hasText(cursor));

        throw new IllegalStateException("官方 ClickHouse MCP 未暴露 run_select_query/run_query 工具");
    }

    private String resolveQueryArgumentName(McpSchema.Tool tool) {
        if (tool == null || tool.inputSchema() == null || tool.inputSchema().properties() == null) {
            return "query";
        }

        Map<String, Object> schemaProperties = tool.inputSchema().properties();
        if (schemaProperties.containsKey("query")) {
            return "query";
        }
        if (schemaProperties.containsKey("sql")) {
            return "sql";
        }
        return schemaProperties.keySet().stream().findFirst().orElse("query");
    }

    private String extractErrorMessage(McpSchema.CallToolResult result) {
        if (result == null || result.content() == null) {
            return "MCP tool 调用失败";
        }

        List<String> texts = new ArrayList<>();
        for (McpSchema.Content item : result.content()) {
            String text = extractText(item);
            if (StringUtils.hasText(text)) {
                texts.add(text);
            }
        }
        if (!texts.isEmpty()) {
            return String.join(" ", texts);
        }
        return "MCP tool 调用失败";
    }

    private List<Map<String, Object>> extractRows(McpSchema.CallToolResult toolResult, String renderedSql) throws IOException {
        if (toolResult == null) {
            throw new IllegalStateException("ClickHouse MCP 未返回结果，SQL=" + renderedSql);
        }

        Object structuredContent = toolResult.structuredContent();
        if (structuredContent != null) {
            JsonNode structuredNode = objectMapper.valueToTree(structuredContent);
            List<Map<String, Object>> rows = rowsFromStructuredContent(structuredNode);
            if (!rows.isEmpty() || structuredNode.has("rows")) {
                return rows;
            }
        }

        if (toolResult.content() != null) {
            for (McpSchema.Content item : toolResult.content()) {
                String text = extractText(item);
                if (!StringUtils.hasText(text)) {
                    continue;
                }
                JsonNode parsed = tryParseJson(text);
                if (parsed != null) {
                    List<Map<String, Object>> rows = rowsFromStructuredContent(parsed);
                    if (!rows.isEmpty() || parsed.has("rows")) {
                        return rows;
                    }
                }
            }
        }

        throw new IllegalStateException("ClickHouse MCP 返回结果无法解析为结构化行数据，SQL=" + renderedSql);
    }

    private String extractText(McpSchema.Content content) {
        if (content instanceof McpSchema.TextContent textContent) {
            return textContent.text();
        }
        JsonNode node = objectMapper.valueToTree(content);
        return node.path("text").asText(null);
    }

    private List<Map<String, Object>> rowsFromStructuredContent(JsonNode structured) {
        if (structured == null || structured.isMissingNode()) {
            return List.of();
        }

        if (structured.isArray()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (JsonNode rowNode : structured) {
                if (rowNode.isObject()) {
                    rows.add(objectMapper.convertValue(rowNode, objectMapper.getTypeFactory()
                            .constructMapType(LinkedHashMap.class, String.class, Object.class)));
                }
            }
            return rows;
        }

        JsonNode rowsNode = structured.path("rows");
        if (!rowsNode.isArray()) {
            return List.of();
        }

        JsonNode columnsNode = structured.path("columns");
        List<String> columns = new ArrayList<>();
        if (columnsNode.isArray()) {
            for (JsonNode columnNode : columnsNode) {
                if (columnNode.isTextual()) {
                    columns.add(columnNode.asText());
                } else if (columnNode.isObject() && columnNode.has("name")) {
                    columns.add(columnNode.path("name").asText());
                }
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (JsonNode rowNode : rowsNode) {
            if (rowNode.isObject()) {
                rows.add(objectMapper.convertValue(rowNode, objectMapper.getTypeFactory()
                        .constructMapType(LinkedHashMap.class, String.class, Object.class)));
                continue;
            }
            if (rowNode.isArray()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < rowNode.size(); i++) {
                    String columnName = i < columns.size() ? columns.get(i) : "col_" + i;
                    row.put(columnName, objectMapper.convertValue(rowNode.get(i), Object.class));
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private JsonNode tryParseJson(String text) throws IOException {
        String trimmed = text.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return null;
        }
        return objectMapper.readTree(trimmed);
    }

    private void closeQuietly(McpSyncClient client) {
        if (client == null) {
            return;
        }
        try {
            if (!client.closeGracefully()) {
                client.close();
            }
        } catch (Exception ex) {
            log.debug("关闭 ClickHouse MCP 客户端失败: {}", ex.getMessage());
            try {
                client.close();
            } catch (Exception ignored) {
                // ignore close exception
            }
        }
    }

    private record Endpoint(String host, int port, boolean secure) {
    }

    private record ToolDescriptor(String name, String argumentName) {
    }

    private record McpClientSession(McpSyncClient client) implements AutoCloseable {

        @Override
        public void close() {
            if (client == null) {
                return;
            }
            try {
                if (!client.closeGracefully()) {
                    client.close();
                }
            } catch (Exception ex) {
                client.close();
            }
        }
    }
}
