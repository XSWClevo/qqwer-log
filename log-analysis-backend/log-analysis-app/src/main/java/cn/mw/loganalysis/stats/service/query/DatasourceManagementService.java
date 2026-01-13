package cn.mw.loganalysis.stats.service.query;

import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 数据源管理服务
 * 提供连接测试、建表、表结构验证等功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourceManagementService {

    private final List<DatasourceOperationStrategy> operationStrategies;
    private final DynamicLogQueryService dynamicLogQueryService;

    /**
     * 测试数据源连接
     */
    public ConnectionTestResult testConnection(String componentId) {
        try {
            DatasourceConnectionConfig config = dynamicLogQueryService.getDatasourceConfigPublic(componentId);
            DatasourceOperationStrategy strategy = getStrategy(config.getType());
            return strategy.testConnection(config);
        } catch (Exception e) {
            log.error("测试连接失败: {}", e.getMessage(), e);
            return ConnectionTestResult.builder()
                    .success(false)
                    .message("连接测试失败: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 检查表是否存在
     */
    public TableCheckResult checkTable(String componentId) {
        try {
            DatasourceConnectionConfig config = dynamicLogQueryService.getDatasourceConfigPublic(componentId);
            DatasourceOperationStrategy strategy = getStrategy(config.getType());
            return strategy.checkTable(config);
        } catch (Exception e) {
            log.error("检查表失败: {}", e.getMessage(), e);
            return TableCheckResult.builder()
                    .exists(false)
                    .message("检查表失败: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 获取建表 SQL（预览）
     */
    public String getCreateTableSQL(String componentId, TableSchema schema) {
        try {
            DatasourceConnectionConfig config = dynamicLogQueryService.getDatasourceConfigPublic(componentId);
            DatasourceOperationStrategy strategy = getStrategy(config.getType());
            return strategy.generateCreateTableSQL(config, schema);
        } catch (Exception e) {
            log.error("生成建表SQL失败: {}", e.getMessage(), e);
            throw new RuntimeException("生成建表SQL失败: " + e.getMessage());
        }
    }

    /**
     * 执行建表
     */
    public CreateTableResult createTable(String componentId, TableSchema schema) {
        try {
            DatasourceConnectionConfig config = dynamicLogQueryService.getDatasourceConfigPublic(componentId);
            DatasourceOperationStrategy strategy = getStrategy(config.getType());
            return strategy.createTable(config, schema);
        } catch (Exception e) {
            log.error("建表失败: {}", e.getMessage(), e);
            return CreateTableResult.builder()
                    .success(false)
                    .message("建表失败: " + e.getMessage())
                    .build();
        }
    }

    /**
     * 获取推荐的表结构（基于 Vector Sink 类型）
     */
    public TableSchema getRecommendedSchema(String componentId) {
        try {
            DatasourceConnectionConfig config = dynamicLogQueryService.getDatasourceConfigPublic(componentId);
            DatasourceOperationStrategy strategy = getStrategy(config.getType());
            return strategy.getRecommendedSchema(config);
        } catch (Exception e) {
            log.error("获取推荐表结构失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取推荐表结构失败: " + e.getMessage());
        }
    }

    /**
     * 获取数据源的所有表列表
     */
    public List<String> listTables(String componentId) {
        try {
            DatasourceConnectionConfig config = dynamicLogQueryService.getDatasourceConfigPublic(componentId);
            DatasourceOperationStrategy strategy = getStrategy(config.getType());
            return strategy.listTables(config);
        } catch (Exception e) {
            log.error("获取表列表失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取表列表失败: " + e.getMessage());
        }
    }

    private DatasourceOperationStrategy getStrategy(String type) {
        if (!StringUtils.hasText(type)) {
            throw new IllegalArgumentException("数据源类型不能为空");
        }

        return operationStrategies.stream()
                .filter(s -> s.getSupportedType().equalsIgnoreCase(type))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的数据源类型: " + type));
    }
}
