package cn.mw.loganalysis.stats.service.query;

import java.util.List;

/**
 * 数据源操作策略接口
 * 提供连接测试、建表等操作
 */
public interface DatasourceOperationStrategy {

    /**
     * 获取支持的数据源类型
     */
    String getSupportedType();

    /**
     * 测试连接
     */
    ConnectionTestResult testConnection(DatasourceConnectionConfig config);

    /**
     * 检查表是否存在
     */
    TableCheckResult checkTable(DatasourceConnectionConfig config);

    /**
     * 生成建表 SQL
     */
    String generateCreateTableSQL(DatasourceConnectionConfig config, TableSchema schema);

    /**
     * 执行建表
     */
    CreateTableResult createTable(DatasourceConnectionConfig config, TableSchema schema);

    /**
     * 获取推荐的表结构
     */
    TableSchema getRecommendedSchema(DatasourceConnectionConfig config);

    /**
     * 获取数据库中的表列表
     */
    List<String> listTables(DatasourceConnectionConfig config);
}
