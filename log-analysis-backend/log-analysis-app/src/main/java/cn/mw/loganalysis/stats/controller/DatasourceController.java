package cn.mw.loganalysis.stats.controller;

import cn.mw.loganalysis.common.response.Result;
import cn.mw.loganalysis.stats.service.query.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数据源管理控制器
 * 提供连接测试、建表、表结构验证等功能
 */
@Slf4j
@RestController
@RequestMapping("/api/datasource")
@RequiredArgsConstructor
public class DatasourceController {

    private final DatasourceManagementService datasourceManagementService;

    /**
     * 测试数据源连接
     */
    @PostMapping("/{componentId}/test-connection")
    public Result<ConnectionTestResult> testConnection(@PathVariable String componentId) {
        log.info("测试数据源连接: {}", componentId);
        return Result.success(datasourceManagementService.testConnection(componentId));
    }

    /**
     * 检查表是否存在
     */
    @GetMapping("/{componentId}/check-table")
    public Result<TableCheckResult> checkTable(@PathVariable String componentId) {
        log.info("检查表: {}", componentId);
        return Result.success(datasourceManagementService.checkTable(componentId));
    }

    /**
     * 获取数据源中的表列表
     */
    @GetMapping("/{componentId}/tables")
    public Result<List<String>> listTables(@PathVariable String componentId) {
        log.info("获取表列表: {}", componentId);
        return Result.success(datasourceManagementService.listTables(componentId));
    }

    /**
     * 获取推荐的表结构
     */
    @GetMapping("/{componentId}/recommended-schema")
    public Result<TableSchema> getRecommendedSchema(@PathVariable String componentId) {
        log.info("获取推荐表结构: {}", componentId);
        return Result.success(datasourceManagementService.getRecommendedSchema(componentId));
    }

    /**
     * 预览建表 SQL
     */
    @PostMapping("/{componentId}/preview-create-table")
    public Result<String> previewCreateTable(
            @PathVariable String componentId,
            @RequestBody TableSchema schema) {
        log.info("预览建表SQL: {}", componentId);
        return Result.success(datasourceManagementService.getCreateTableSQL(componentId, schema));
    }

    /**
     * 执行建表
     */
    @PostMapping("/{componentId}/create-table")
    public Result<CreateTableResult> createTable(
            @PathVariable String componentId,
            @RequestBody TableSchema schema) {
        log.info("执行建表: {}", componentId);
        return Result.success(datasourceManagementService.createTable(componentId, schema));
    }
}
