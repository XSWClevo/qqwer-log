package cn.mw.loganalysis.stats.service.query;

import lombok.Builder;
import lombok.Data;

/**
 * 数据源连接配置
 * 从 ConfigComponent.configYaml 解析得到
 */
@Data
@Builder
public class DatasourceConnectionConfig {

    /**
     * 数据源类型: clickhouse, elasticsearch, postgresql, mysql, kafka, loki
     */
    private String type;

    /**
     * 连接端点 (host:port 或完整 URL)
     */
    private String endpoint;

    /**
     * 数据库名
     */
    private String database;

    /**
     * 表名/索引名
     */
    private String table;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 是否使用 TLS/SSL
     */
    private Boolean tls;

    /**
     * 额外配置（JSON 格式）
     */
    private String extraConfig;

    /**
     * 原始 YAML 配置
     */
    private String rawYaml;

    /**
     * 组件 ID
     */
    private String componentId;

    /**
     * 组件名称
     */
    private String componentName;
}
