package cn.mw.loganalysis.stats.service.query;

import cn.mw.loganalysis.stats.dto.LogContextRequest;
import cn.mw.loganalysis.stats.dto.LogQueryRequest;
import cn.mw.loganalysis.stats.dto.StatsQueryRequest;

import java.util.List;
import java.util.Map;

/**
 * 日志查询策略接口
 * 不同类型的数据源（ClickHouse、Elasticsearch、PostgreSQL）实现此接口
 */
public interface LogQueryStrategy {

    /**
     * 获取支持的数据源类型
     * @return 数据源类型标识，如 "clickhouse", "elasticsearch", "postgresql"
     */
    String getSupportedType();

    /**
     * 获取数据源的表结构（字段列表）
     * @param connectionConfig 连接配置
     * @return 字段信息列表
     */
    List<FieldInfo> getTableSchema(DatasourceConnectionConfig connectionConfig);

    /**
     * 查询日志
     * @param request 查询请求
     * @param connectionConfig 连接配置（从 ConfigComponent.configYaml 解析）
     * @return 查询结果
     */
    Map<String, Object> queryLogs(LogQueryRequest request, DatasourceConnectionConfig connectionConfig);

    /**
     * 查询日志上下文
     * @param request 上下文查询请求
     * @param connectionConfig 连接配置
     * @return 上下文日志
     */
    Map<String, Object> queryLogContext(LogContextRequest request, DatasourceConnectionConfig connectionConfig);

    /**
     * 统计查询
     * @param request 统计请求
     * @param connectionConfig 连接配置
     * @return 统计结果
     */
    Map<String, Object> queryStats(StatsQueryRequest request, DatasourceConnectionConfig connectionConfig);

    /**
     * 时间序列查询
     * @param request 查询请求
     * @param connectionConfig 连接配置
     * @return 时间序列数据
     */
    Map<String, Object> queryTimeSeries(StatsQueryRequest request, DatasourceConnectionConfig connectionConfig);
}
