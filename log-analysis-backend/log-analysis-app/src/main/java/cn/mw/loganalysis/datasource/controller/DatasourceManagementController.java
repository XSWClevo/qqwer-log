package cn.mw.loganalysis.datasource.controller;

import cn.mw.loganalysis.common.response.Result;
import cn.mw.loganalysis.datasource.dto.CreateDatasourceRequest;
import cn.mw.loganalysis.datasource.dto.DatasourceTestResult;
import cn.mw.loganalysis.datasource.dto.UpdateDatasourceRequest;
import cn.mw.loganalysis.datasource.entity.Datasource;
import cn.mw.loganalysis.datasource.service.DatasourceService;
import cn.mw.loganalysis.operationlog.annotation.OperationLog;
import cn.mw.loganalysis.operationlog.enums.OperationAction;
import cn.mw.loganalysis.operationlog.enums.OperationModule;
import cn.mw.loganalysis.operationlog.enums.OperationType;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 数据源管理控制器
 * 提供数据源的 CRUD 和连接测试功能
 */
@Slf4j
@RestController
@RequestMapping("/api/datasources")
@RequiredArgsConstructor
public class DatasourceManagementController {

    private final DatasourceService datasourceService;

    /**
     * 创建数据源
     */
    @PostMapping
    @OperationLog(
        module = OperationModule.DATASOURCE,
        operationType = OperationType.CREATE,
        action = OperationAction.CREATE_DATASOURCE,
        resourceType = "Datasource",
        resourceIdSpEL = "#result.data.id",
        sensitiveFields = {"password"}
    )
    public Result<Datasource> createDatasource(@Valid @RequestBody CreateDatasourceRequest request) {
        log.info("创建数据源: name={}, type={}", request.getName(), request.getType());
        Datasource datasource = datasourceService.createDatasource(request);
        return Result.success(datasource);
    }

    /**
     * 更新数据源
     */
    @PutMapping("/{id}")
    @OperationLog(
        module = OperationModule.DATASOURCE,
        operationType = OperationType.UPDATE,
        action = OperationAction.UPDATE_DATASOURCE,
        resourceType = "Datasource",
        resourceIdSpEL = "#id",
        sensitiveFields = {"password"}
    )
    public Result<Datasource> updateDatasource(
            @PathVariable String id,
            @Valid @RequestBody UpdateDatasourceRequest request) {
        log.info("更新数据源: id={}", id);
        Datasource datasource = datasourceService.updateDatasource(id, request);
        return Result.success(datasource);
    }

    /**
     * 删除数据源
     */
    @DeleteMapping("/{id}")
    @OperationLog(
        module = OperationModule.DATASOURCE,
        operationType = OperationType.DELETE,
        action = OperationAction.DELETE_DATASOURCE,
        resourceType = "Datasource",
        resourceIdSpEL = "#id"
    )
    public Result<Void> deleteDatasource(@PathVariable String id) {
        log.info("删除数据源: id={}", id);
        datasourceService.deleteDatasource(id);
        return Result.success();
    }

    /**
     * 获取数据源详情
     */
    @GetMapping("/{id}")
    public Result<Datasource> getDatasource(@PathVariable String id) {
        Datasource datasource = datasourceService.getDatasource(id);
        if (datasource == null) {
            return Result.notFound("数据源不存在");
        }
        return Result.success(datasource);
    }

    /**
     * 分页查询数据源列表
     */
    @GetMapping
    public Result<Page<Datasource>> listDatasources(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status) {
        log.info("查询数据源列表: pageNum={}, pageSize={}, keyword={}, type={}, status={}",
                pageNum, pageSize, keyword, type, status);
        Page<Datasource> page = datasourceService.listDatasources(pageNum, pageSize, keyword, type, status);
        return Result.success(page);
    }

    /**
     * 获取所有活跃的数据源
     */
    @GetMapping("/active")
    public Result<List<Datasource>> listActiveDatasources() {
        List<Datasource> datasources = datasourceService.listActiveDatasources();
        return Result.success(datasources);
    }

    /**
     * 根据类型查询数据源
     */
    @GetMapping("/by-type/{type}")
    public Result<List<Datasource>> listDatasourcesByType(@PathVariable String type) {
        List<Datasource> datasources = datasourceService.listDatasourcesByType(type);
        return Result.success(datasources);
    }

    /**
     * 测试数据源连接
     */
    @PostMapping("/{id}/test")
    @OperationLog(
        module = OperationModule.DATASOURCE,
        operationType = OperationType.EXECUTE,
        action = OperationAction.TEST_DATASOURCE_CONNECTION,
        resourceType = "Datasource",
        resourceIdSpEL = "#id"
    )
    public Result<DatasourceTestResult> testConnection(@PathVariable String id) {
        log.info("测试数据源连接: id={}", id);
        DatasourceTestResult result = datasourceService.testConnection(id);
        return Result.success(result);
    }

    /**
     * 测试新数据源连接（创建前测试）
     */
    @PostMapping("/test")
    public Result<DatasourceTestResult> testNewConnection(@Valid @RequestBody CreateDatasourceRequest request) {
        log.info("测试新数据源连接: name={}, type={}", request.getName(), request.getType());

        // 创建临时数据源对象用于测试
        Datasource tempDatasource = new Datasource();
        tempDatasource.setType(request.getType());
        tempDatasource.setHost(request.getHost());
        tempDatasource.setPort(request.getPort());
        tempDatasource.setDatabaseName(request.getDatabaseName());
        tempDatasource.setUsername(request.getUsername());
        tempDatasource.setPassword(request.getPassword());
        tempDatasource.setSslEnabled(request.getSslEnabled());
        tempDatasource.setConnectionParams(request.getConnectionParams());

        DatasourceTestResult result = datasourceService.testConnection(tempDatasource);
        return Result.success(result);
    }
}
