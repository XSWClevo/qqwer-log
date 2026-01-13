package cn.mw.loganalysis.vector.controller;

import cn.mw.loganalysis.common.response.Result;
import cn.mw.loganalysis.vector.dto.DeployConfigRequest;
import cn.mw.loganalysis.vector.entity.VectorDeployment;
import cn.mw.loganalysis.vector.service.VectorDeploymentService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Vector配置部署控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/vector/deployments")
@RequiredArgsConstructor
public class VectorDeploymentController {

    private final VectorDeploymentService deploymentService;

    /**
     * 创建部署任务
     */
    @PostMapping
    public Result<List<VectorDeployment>> createDeployment(@Validated @RequestBody DeployConfigRequest request) {
        // TODO: 从Security Context获取当前用户ID
        String userId = "system";
        List<VectorDeployment> deployments = deploymentService.createDeployments(request, userId);
        return Result.success(deployments);
    }

    /**
     * 分页查询部署记录
     */
    @GetMapping
    public Result<Page<VectorDeployment>> getDeployments(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String machineId,
            @RequestParam(required = false) String configId,
            @RequestParam(required = false) String status) {
        Page<VectorDeployment> page = deploymentService.getDeployments(pageNum, pageSize, machineId, configId, status);
        return Result.success(page);
    }

    /**
     * 根据ID查询部署详情
     */
    @GetMapping("/{id}")
    public Result<VectorDeployment> getDeploymentById(@PathVariable String id) {
        VectorDeployment deployment = deploymentService.getDeploymentById(id);
        if (deployment == null) {
            return Result.error("部署记录不存在");
        }
        return Result.success(deployment);
    }

    /**
     * 根据机器ID查询部署记录
     */
    @GetMapping("/machine/{machineId}")
    public Result<List<VectorDeployment>> getDeploymentsByMachine(@PathVariable String machineId) {
        List<VectorDeployment> deployments = deploymentService.getDeploymentsByMachineId(machineId);
        return Result.success(deployments);
    }
}
