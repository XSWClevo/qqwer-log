package cn.mw.loganalysis.stats.service.query;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * ClickHouse 官方 MCP Server 配置。
 *
 * 这里仍然采用 stdio 方式启动 mcp-clickhouse 进程，但底层客户端已经切到官方 Java SDK。
 * 保留 stdio 的原因很直接：当前项目的数据源是动态的，同一个后端实例下可能存在多个
 * ClickHouse Sink，所以每次查询都需要按当前数据源把连接信息通过环境变量传给子进程。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "agent.mcp.clickhouse")
public class ClickHouseMcpProperties {

    /**
     * 总开关。关闭时 ClickHouse 仍然走现有 JDBC 查询链路。
     */
    private boolean enabled = false;

    /**
     * MCP 调用失败时，是否回退到现有 JDBC 查询。
     * 默认开启，避免因为 MCP 服务未安装或暂时异常直接影响现有功能。
     */
    private boolean fallbackToJdbcOnError = true;

    /**
     * 启动 MCP 进程的可执行文件，例如 uvx / python3。
     */
    private String executable;

    /**
     * 启动参数，例如:
     * - ["mcp-clickhouse"]
     * - ["-m", "mcp_clickhouse.main"]
     */
    private List<String> arguments = new ArrayList<>();

    /**
     * MCP initialize / tools/list / tools/call 的超时时间。
     */
    private Duration requestTimeout = Duration.ofSeconds(30);

    /**
     * 启动进程后等待首个响应的超时时间。
     */
    private Duration startupTimeout = Duration.ofSeconds(10);

    /**
     * 传给官方 mcp-clickhouse 的 TLS 校验开关。
     */
    private boolean verifySsl = true;

    /**
     * 传给官方 mcp-clickhouse 的连接超时秒数。
     */
    private int connectTimeoutSeconds = 10;

    /**
     * 传给官方 mcp-clickhouse 的读写超时秒数。
     */
    private int sendReceiveTimeoutSeconds = 30;

    public boolean hasExecutable() {
        return executable != null && !executable.isBlank();
    }
}
