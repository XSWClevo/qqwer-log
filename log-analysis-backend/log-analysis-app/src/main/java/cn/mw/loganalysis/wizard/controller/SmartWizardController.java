package cn.mw.loganalysis.wizard.controller;

import cn.mw.loganalysis.common.response.Result;
import cn.mw.loganalysis.vector.dto.VrlExecuteRequest;
import cn.mw.loganalysis.wizard.dto.CreateTableRequest;
import cn.mw.loganalysis.wizard.dto.CreateTableResponse;
import cn.mw.loganalysis.wizard.dto.GenerateDDLRequest;
import cn.mw.loganalysis.wizard.dto.GenerateDDLResponse;
import cn.mw.loganalysis.wizard.dto.ParseLogResponse;
import cn.mw.loganalysis.wizard.service.ClickHouseDDLGenerator;
import cn.mw.loganalysis.wizard.service.SmartWizardService;
import cn.mw.loganalysis.wizard.service.TableManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 智能向导控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/wizard")
@RequiredArgsConstructor
public class SmartWizardController {

    private final SmartWizardService smartWizardService;
    private final ClickHouseDDLGenerator clickHouseDDLGenerator;
    private final TableManagementService tableManagementService;

    /**
     * 解析日志样本
     */
    @PostMapping("/parse-log")
    public Result<ParseLogResponse> parseLog(@Valid @RequestBody VrlExecuteRequest request) {
        ParseLogResponse response = smartWizardService.parseLog(request);
        return Result.success(response);
    }

    /**
     * 生成 DDL
     */
    @PostMapping("/generate-ddl")
    public Result<GenerateDDLResponse> generateDDL(@Valid @RequestBody GenerateDDLRequest request) {
        GenerateDDLResponse response = clickHouseDDLGenerator.generate(request);
        return Result.success(response);
    }

    /**
     * 创建表（并自动创建对应的 Vector 组件）
     */
    @PostMapping("/create-table")
    public Result<CreateTableResponse> createTable(@Valid @RequestBody CreateTableRequest request) {
        CreateTableResponse response = tableManagementService.createTableWithComponents(request);
        return Result.success(response);
    }

    /**
     * 查询表列表
     */
    @PostMapping("/list-tables")
    public Result<List<Map<String, Object>>> listTables(@RequestBody Map<String, String> request) {
        String datasourceId = request.get("datasourceId");
        List<Map<String, Object>> tables = tableManagementService.listTables(datasourceId);
        return Result.success(tables);
    }

    /**
     * 查询表结构
     */
    @PostMapping("/describe-table")
    public Result<List<Map<String, Object>>> describeTable(@RequestBody Map<String, String> request) {
        String datasourceId = request.get("datasourceId");
        String tableName = request.get("tableName");
        List<Map<String, Object>> columns = tableManagementService.describeTable(datasourceId, tableName);
        return Result.success(columns);
    }

    /**
     * 添加字段
     */
    @PostMapping("/add-column")
    public Result<Void> addColumn(@RequestBody Map<String, String> request) {
        String datasourceId = request.get("datasourceId");
        String tableName = request.get("tableName");
        String columnName = request.get("columnName");
        String columnType = request.get("columnType");
        String comment = request.get("comment");

        tableManagementService.addColumn(datasourceId, tableName, columnName, columnType, comment);
        return Result.success();
    }
}
