package cn.mw.loganalysis.vector.controller;

import cn.mw.loganalysis.common.response.Result;
import cn.mw.loganalysis.operationlog.annotation.OperationLog;
import cn.mw.loganalysis.operationlog.enums.OperationAction;
import cn.mw.loganalysis.operationlog.enums.OperationModule;
import cn.mw.loganalysis.operationlog.enums.OperationType;
import cn.mw.loganalysis.vector.dto.AddMachineRequest;
import cn.mw.loganalysis.vector.entity.VectorMachine;
import cn.mw.loganalysis.vector.service.VectorMachineService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Vector机器管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/vector/machines")
@RequiredArgsConstructor
public class VectorMachineController {

    private final VectorMachineService vectorMachineService;

    /**
     * 分页查询机器列表
     */
    @GetMapping("/page")
    public Result<Page<VectorMachine>> getMachinePage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        Page<VectorMachine> page = vectorMachineService.getMachinePage(pageNum, pageSize, keyword, status);
        return Result.success(page);
    }

    /**
     * 根据状态查询机器列表
     */
    @GetMapping("/list")
    public Result<List<VectorMachine>> getMachinesByStatus(@RequestParam(required = false) String status) {
        List<VectorMachine> machines = status != null
                ? vectorMachineService.getMachinesByStatus(status)
                : vectorMachineService.getMachinePage(1, 1000, null, null).getRecords();
        return Result.success(machines);
    }

    /**
     * 根据ID查询机器详情
     */
    @GetMapping("/{id}")
    public Result<VectorMachine> getMachineById(@PathVariable String id) {
        VectorMachine machine = vectorMachineService.getMachineById(id);
        if (machine == null) {
            return Result.error("机器不存在");
        }
        return Result.success(machine);
    }

    /**
     * 添加机器
     */
    @PostMapping
    @OperationLog(
        module = OperationModule.VECTOR,
        operationType = OperationType.CREATE,
        action = OperationAction.ADD_VECTOR_MACHINE,
        resourceType = "VectorMachine",
        resourceIdSpEL = "#result.data.id"
    )
    public Result<VectorMachine> addMachine(@Validated @RequestBody AddMachineRequest request) {
        // TODO: 从Security Context获取当前用户ID
        String userId = "system";
        VectorMachine machine = vectorMachineService.addMachine(request, userId);
        return Result.success(machine);
    }

    /**
     * 更新机器信息
     */
    @PutMapping("/{id}")
    @OperationLog(
        module = OperationModule.VECTOR,
        operationType = OperationType.UPDATE,
        action = OperationAction.UPDATE_VECTOR_MACHINE,
        resourceType = "VectorMachine",
        resourceIdSpEL = "#id"
    )
    public Result<VectorMachine> updateMachine(@PathVariable String id,
                                                @Validated @RequestBody AddMachineRequest request) {
        VectorMachine machine = vectorMachineService.updateMachine(id, request);
        return Result.success(machine);
    }

    /**
     * 删除机器
     */
    @DeleteMapping("/{id}")
    @OperationLog(
        module = OperationModule.VECTOR,
        operationType = OperationType.DELETE,
        action = OperationAction.DELETE_VECTOR_MACHINE,
        resourceType = "VectorMachine",
        resourceIdSpEL = "#id"
    )
    public Result<Void> deleteMachine(@PathVariable String id) {
        vectorMachineService.deleteMachine(id);
        return Result.success();
    }

    /**
     * 更新机器状态
     */
    @PutMapping("/{id}/status")
    @OperationLog(
        module = OperationModule.VECTOR,
        operationType = OperationType.UPDATE,
        action = OperationAction.UPDATE_VECTOR_MACHINE_STATUS,
        resourceType = "VectorMachine",
        resourceIdSpEL = "#id"
    )
    public Result<Void> updateMachineStatus(@PathVariable String id,
                                             @RequestParam String status) {
        vectorMachineService.updateMachineStatus(id, status);
        return Result.success();
    }

    /**
     * 更新心跳
     */
    @PostMapping("/{id}/heartbeat")
    public Result<Void> updateHeartbeat(@PathVariable String id) {
        vectorMachineService.updateHeartbeat(id);
        return Result.success();
    }

    /**
     * 生成 Agent Token
     */
    @PostMapping("/generate-token")
    public Result<java.util.Map<String, String>> generateToken() {
        String token = java.util.UUID.randomUUID().toString().replace("-", "");
        return Result.success(java.util.Map.of("token", token));
    }
}
