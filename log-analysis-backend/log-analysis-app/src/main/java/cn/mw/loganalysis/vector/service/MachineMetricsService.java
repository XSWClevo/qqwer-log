package cn.mw.loganalysis.vector.service;

import cn.mw.loganalysis.vector.dto.AgentMetricsRequest;
import cn.mw.loganalysis.vector.dto.MachineMetricsDTO;
import cn.mw.loganalysis.vector.entity.MachineMetrics;
import cn.mw.loganalysis.vector.mapper.MachineMetricsMapper;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 机器指标服务
 * 存储和查询机器的系统指标历史数据
 * 
 * 架构：内存缓存（实时） + ClickHouse（持久化）
 */
@Slf4j
@Service
public class MachineMetricsService {

    private final MachineMetricsMapper metricsMapper;
    private final ObjectMapper objectMapper;

    /**
     * 内存中的历史缓存（用于快速查询最近数据）
     * key: machineId
     */
    private final Map<String, LinkedList<MachineMetricsDTO.MetricsPoint>> memoryCache = new ConcurrentHashMap<>();

    /**
     * 内存缓存保留的数据点数量
     */
    private static final int MEMORY_CACHE_SIZE = 30;

    public MachineMetricsService(MachineMetricsMapper metricsMapper, ObjectMapper objectMapper) {
        this.metricsMapper = metricsMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 记录指标数据
     */
    @DS("clickhouse")
    public void recordMetrics(String machineId, AgentMetricsRequest request) {
        if (machineId == null || request == null) {
            return;
        }

        MachineMetricsDTO.MetricsPoint point = new MachineMetricsDTO.MetricsPoint();
        point.setTimestamp(request.getCollectedAt() != null ? request.getCollectedAt() : LocalDateTime.now());
        point.setCpuUsagePercent(request.getCpuUsagePercent());
        point.setMemoryUsagePercent(request.getMemoryUsagePercent());
        point.setMemoryUsedMb(request.getMemoryUsedMb());
        point.setDiskUsagePercent(request.getDiskUsagePercent());
        point.setDiskUsedGb(request.getDiskUsedGb());
        point.setAgentMemoryMb(request.getAgentMemoryMb());
        point.setVectorRunning(request.getVectorRunning());
        
        // 转换网卡信息
        if (request.getNetworkInterfaces() != null && !request.getNetworkInterfaces().isEmpty()) {
            List<MachineMetricsDTO.NetworkInterfaceInfo> networkInfos = request.getNetworkInterfaces().stream()
                    .map(ni -> {
                        MachineMetricsDTO.NetworkInterfaceInfo info = new MachineMetricsDTO.NetworkInterfaceInfo();
                        info.setName(ni.getName());
                        info.setBytesSent(ni.getBytesSent());
                        info.setBytesRecv(ni.getBytesRecv());
                        info.setPacketsSent(ni.getPacketsSent());
                        info.setPacketsRecv(ni.getPacketsRecv());
                        info.setErrin(ni.getErrin());
                        info.setErrout(ni.getErrout());
                        return info;
                    })
                    .collect(Collectors.toList());
            point.setNetworkInterfaces(networkInfos);
        }

        // 3. 同步写入 ClickHouse（先用同步确保能写入）
        try {
            MachineMetrics entity = new MachineMetrics();
            entity.setMachineId(machineId);
            entity.setCollectedAt(request.getCollectedAt() != null ? request.getCollectedAt() : LocalDateTime.now());
            entity.setCpuUsagePercent(request.getCpuUsagePercent());
            entity.setMemoryUsagePercent(request.getMemoryUsagePercent());
            entity.setMemoryUsedMb(request.getMemoryUsedMb());
            entity.setDiskUsagePercent(request.getDiskUsagePercent());
            entity.setDiskUsedGb(request.getDiskUsedGb());
            entity.setAgentMemoryMb(request.getAgentMemoryMb());
            entity.setVectorRunning((request.getVectorRunning() != null && request.getVectorRunning()) ? 1 : 0);
            entity.setCreatedAt(LocalDateTime.now());

            // 序列化网卡信息为 JSON
            if (request.getNetworkInterfaces() != null && !request.getNetworkInterfaces().isEmpty()) {
                try {
                    String networkJson = objectMapper.writeValueAsString(request.getNetworkInterfaces());
                    entity.setNetworkInterfaces(networkJson);
                } catch (JsonProcessingException e) {
                    log.error("序列化网卡信息失败: machineId={}", machineId, e);
                    entity.setNetworkInterfaces("[]");
                }
            } else {
                entity.setNetworkInterfaces("[]");
            }

            metricsMapper.insert(entity);
            log.info("指标已写入 ClickHouse: machineId={}, cpu={}%, mem={}%",
                    machineId, request.getCpuUsagePercent(), request.getMemoryUsagePercent());
        } catch (Exception e) {
            log.error("写入 ClickHouse 失败: machineId={}", machineId, e);
        }

        log.debug("记录机器 {} 的指标: cpu={}%, mem={}%",
                machineId, point.getCpuUsagePercent(), point.getMemoryUsagePercent());
    }

    /**
     * 获取机器的最新指标
     * 优先从内存缓存获取，没有则查询 ClickHouse
     */
    public MachineMetricsDTO.MetricsPoint getLatestMetrics(String machineId) {

        // 2. 内存没有，查 ClickHouse
        try {
            MachineMetrics entity = metricsMapper.selectLatestByMachineId(machineId);
            if (entity != null) {
                return entityToPoint(entity);
            }
        } catch (Exception e) {
            log.error("从 ClickHouse 查询最新指标失败: machineId={}, error={}", machineId, e.getMessage());
        }
        
        return null;
    }

    /**
     * 获取机器的指标历史
     * 优先从内存缓存获取，如果需要更多数据则查询 ClickHouse
     * 
     * @param machineId 机器ID
     * @param minutes 获取最近多少分钟的数据
     */
    public List<MachineMetricsDTO.MetricsPoint> getMetricsHistory(String machineId, int minutes) {

        // 从 ClickHouse 查询
        return getMetricsHistoryFromClickHouse(machineId, minutes);
    }

    /**
     * 从 ClickHouse 查询指标历史
     */
    private List<MachineMetricsDTO.MetricsPoint> getMetricsHistoryFromClickHouse(String machineId, int minutes) {
        try {
            LocalDateTime endTime = LocalDateTime.now();
            LocalDateTime startTime = endTime.minusMinutes(minutes);

            List<MachineMetrics> entities = metricsMapper.selectByMachineIdAndTimeRange(
                    machineId, startTime, endTime);

            return entities.stream()
                    .map(this::entityToPoint)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("从 ClickHouse 查询指标失败: machineId={}, error={}", machineId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取完整的机器指标数据（包含最新值和历史）
     */
    public MachineMetricsDTO getMachineMetrics(String machineId, int historyMinutes) {
        MachineMetricsDTO dto = new MachineMetricsDTO();
        dto.setMachineId(machineId);
        dto.setLatest(getLatestMetrics(machineId));
        dto.setHistory(getMetricsHistory(machineId, historyMinutes));
        return dto;
    }

    /**
     * 实体转换为 DTO
     */
    private MachineMetricsDTO.MetricsPoint entityToPoint(MachineMetrics entity) {
        MachineMetricsDTO.MetricsPoint point = new MachineMetricsDTO.MetricsPoint();
        point.setTimestamp(entity.getCollectedAt());
        point.setCpuUsagePercent(entity.getCpuUsagePercent());
        point.setMemoryUsagePercent(entity.getMemoryUsagePercent());
        point.setMemoryUsedMb(entity.getMemoryUsedMb());
        point.setDiskUsagePercent(entity.getDiskUsagePercent());
        point.setDiskUsedGb(entity.getDiskUsedGb());
        point.setAgentMemoryMb(entity.getAgentMemoryMb());
        // ClickHouse UInt8 转布尔值
        point.setVectorRunning(entity.getVectorRunningAsBool());

        // 反序列化网卡信息
        if (entity.getNetworkInterfaces() != null && !entity.getNetworkInterfaces().isEmpty()) {
            try {
                List<MachineMetricsDTO.NetworkInterfaceInfo> networkInfos = objectMapper.readValue(
                        entity.getNetworkInterfaces(),
                        new TypeReference<>() {}
                );
                point.setNetworkInterfaces(networkInfos);
            } catch (JsonProcessingException e) {
                log.error("反序列化网卡信息失败: {}", entity.getNetworkInterfaces(), e);
                point.setNetworkInterfaces(Collections.emptyList());
            }
        } else {
            point.setNetworkInterfaces(Collections.emptyList());
        }

        return point;
    }
}
