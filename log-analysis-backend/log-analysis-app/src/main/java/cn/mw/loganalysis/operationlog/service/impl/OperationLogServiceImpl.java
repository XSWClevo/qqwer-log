package cn.mw.loganalysis.operationlog.service.impl;

import cn.mw.loganalysis.operationlog.converter.OperationLogConverter;
import cn.mw.loganalysis.operationlog.dto.request.QueryOperationLogRequest;
import cn.mw.loganalysis.operationlog.dto.response.OperationLogDTO;
import cn.mw.loganalysis.operationlog.dto.response.OperationStatsDTO;
import cn.mw.loganalysis.operationlog.entity.UserOperationLog;
import cn.mw.loganalysis.operationlog.mapper.UserOperationLogMapper;
import cn.mw.loganalysis.operationlog.repository.UserOperationLogRepository;
import cn.mw.loganalysis.operationlog.service.OperationLogService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 操作日志 Service 实现类
 *
 * @author Claude
 * @since 2026-01-07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl extends ServiceImpl<UserOperationLogMapper, UserOperationLog> implements OperationLogService {

    private final UserOperationLogMapper operationLogMapper;
    private final UserOperationLogRepository operationLogRepository;
    private final OperationLogConverter operationLogConverter;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveLog(UserOperationLog operationLog) {
        log.info("========= [Service] saveLog called: user={}, action={}",
            operationLog.getUsername(), operationLog.getAction());
        try {
            operationLogRepository.save(operationLog);
            log.info("========= [Service] insert completed, id={}", operationLog.getId());
        } catch (Exception e) {
            log.error("========= [Service] insert failed", e);
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSaveLog(List<UserOperationLog> operationLogs) {
        if (operationLogs == null || operationLogs.isEmpty()) {
            return;
        }

        // MyBatis Plus 批量插入
        for (UserOperationLog log : operationLogs) {
            operationLogRepository.save(log);
        }
    }

    @Override
    public Page<OperationLogDTO> queryLogs(QueryOperationLogRequest request) {
        Page<UserOperationLog> page = new Page<>(request.getPageNum(), request.getPageSize());

        Page<UserOperationLog> resultPage = operationLogRepository.findPageByCondition(
            page,
            request.getUserId(),
            request.getUsername(),
            request.getOperationType(),
            request.getModule(),
            request.getIsSuccess(),
            request.getIpAddress(),
            request.getStartTime(),
            request.getEndTime()
        );

        // 转换为 DTO
        Page<OperationLogDTO> dtoPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        dtoPage.setRecords(operationLogConverter.toDTOList(resultPage.getRecords()));

        return dtoPage;
    }

    @Override
    public OperationLogDTO getLogById(Long id) {
        UserOperationLog entity = operationLogRepository.findById(id);
        return entity != null ? operationLogConverter.toDTO(entity) : null;
    }

    @Override
    public List<OperationLogDTO> getRecentLogsByUserId(Long userId, int limit) {
        List<UserOperationLog> logs = operationLogRepository.findRecentByUserId(userId, limit);
        return operationLogConverter.toDTOList(logs);
    }

    @Override
    public List<OperationStatsDTO> statsByOperationType(String startTime, String endTime) {
        return operationLogMapper.selectStatsByOperationType(startTime, endTime);
    }

    @Override
    public List<OperationStatsDTO> statsByModule(String startTime, String endTime) {
        return operationLogMapper.selectStatsByModule(startTime, endTime);
    }

    @Override
    public List<OperationStatsDTO> statsByUser(String startTime, String endTime, int limit) {
        return operationLogMapper.selectStatsByUser(startTime, endTime, limit);
    }

    @Override
    public Map<String, String> detectAnomalousOperations(Long userId, String ipAddress) {
        Map<String, String> alerts = new HashMap<>();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime fiveMinutesAgo = now.minusMinutes(5);

        // 检测 1: 高频失败 (5分钟内失败 > 20次)
        Long failureCount = operationLogRepository.countFailuresByUserIdAndTime(userId, fiveMinutesAgo, now);
        if (failureCount > 20) {
            alerts.put("HIGH_FREQUENCY_FAILURE",
                String.format("用户 %d 在5分钟内失败 %d 次，可能是密码爆破或系统异常", userId, failureCount));
        }

        // 检测 2: 异常 IP 登录 (新 IP 登录成功)
        boolean isNewIp = !operationLogRepository.existsByUserIdAndIp(userId, ipAddress);
        if (isNewIp) {
            alerts.put("NEW_IP_LOGIN",
                String.format("用户 %d 从新 IP %s 登录成功，请确认是否为本人操作", userId, ipAddress));
        }

        // 检测 3: 批量删除 (5分钟内删除 > 10次)
        Long deleteCount = operationLogMapper.countDeleteOperationsByUserIdAndTime(userId, fiveMinutesAgo, now);
        if (deleteCount != null && deleteCount > 10) {
            alerts.put("BATCH_DELETE",
                String.format("用户 %d 在5分钟内删除 %d 次，可能是误操作或恶意行为", userId, deleteCount));
        }

        return alerts;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int archiveOldLogs() {
        // 归档 6 个月前的数据
        LocalDateTime cutoffDate = LocalDateTime.now().minusMonths(6);

        try {
            // 1. 复制数据到归档表
            int archivedCount = operationLogMapper.insertArchiveBefore(cutoffDate);

            // 2. 删除主表旧数据
            operationLogMapper.deleteLogsBefore(cutoffDate);

            log.info("Successfully archived {} operation logs before {}", archivedCount, cutoffDate);
            return archivedCount;
        } catch (Exception e) {
            log.error("Failed to archive operation logs", e);
            throw e;
        }
    }
}
