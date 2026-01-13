package cn.mw.loganalysis.vector.controller;

import cn.mw.loganalysis.common.response.Result;
import cn.mw.loganalysis.vector.dto.MachineDetailDTO;
import cn.mw.loganalysis.vector.dto.MachineMetricsDTO;
import cn.mw.loganalysis.vector.entity.VectorMachine;
import cn.mw.loganalysis.vector.service.MachineMetricsService;
import cn.mw.loganalysis.vector.service.VectorMachineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 机器指标控制器
 * 提供机器详情和指标查询接口
 */
@Slf4j
@RestController
@RequestMapping("/api/vector/machines")
@RequiredArgsConstructor
public class MachineMetricsController {

    private final VectorMachineService machineService;
    private final MachineMetricsService metricsService;

    /**
     * 获取机器详情（包含基本信息和最新指标）
     */
    @GetMapping("/{id}/detail")
    public Result<MachineDetailDTO> getMachineDetail(@PathVariable String id) {
        VectorMachine machine = machineService.getMachineById(id);
        if (machine == null) {
            return Result.notFound("机器不存在");
        }

        MachineDetailDTO dto = new MachineDetailDTO();
        
        // 基本信息
        dto.setId(machine.getId());
        dto.setName(machine.getName());
        dto.setHostname(machine.getHostname());
        dto.setIpAddress(machine.getIpAddress());
        dto.setStatus(machine.getStatus());
        dto.setOsType(machine.getOsType());
        dto.setVectorVersion(machine.getVectorVersion());
        dto.setAgentVersion(machine.getAgentVersion());
        dto.setLastHeartbeat(machine.getLastHeartbeat());
        dto.setCreatedAt(machine.getCreatedAt());

        // 最新指标
        MachineMetricsDTO.MetricsPoint latest = metricsService.getLatestMetrics(id);
        dto.setLatestMetrics(latest);

        return Result.success(dto);
    }

    /**
     * 获取机器指标历史
     * 
     * @param id 机器ID
     * @param minutes 获取最近多少分钟的数据，默认 30 分钟
     */
    @GetMapping("/{id}/metrics")
    public Result<MachineMetricsDTO> getMachineMetrics(
            @PathVariable String id,
            @RequestParam(defaultValue = "30") int minutes) {
        
        VectorMachine machine = machineService.getMachineById(id);
        if (machine == null) {
            return Result.notFound("机器不存在");
        }

        MachineMetricsDTO dto = metricsService.getMachineMetrics(id, minutes);
        return Result.success(dto);
    }

    /**
     * 获取机器最新指标（轻量级接口，用于实时刷新）
     */
    @GetMapping("/{id}/metrics/latest")
    public Result<MachineMetricsDTO.MetricsPoint> getLatestMetrics(@PathVariable String id) {
        MachineMetricsDTO.MetricsPoint latest = metricsService.getLatestMetrics(id);
        if (latest == null) {
            return Result.success(null);
        }
        return Result.success(latest);
    }
}
