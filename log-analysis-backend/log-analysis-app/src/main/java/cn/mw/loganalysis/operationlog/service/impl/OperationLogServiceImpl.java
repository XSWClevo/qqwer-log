package cn.mw.loganalysis.operationlog.service.impl;

import cn.mw.loganalysis.operationlog.converter.OperationLogConverter;
import cn.mw.loganalysis.operationlog.dto.request.QueryOperationLogRequest;
import cn.mw.loganalysis.operationlog.dto.response.OperationLogDTO;
import cn.mw.loganalysis.operationlog.dto.response.OperationStatsDTO;
import cn.mw.loganalysis.operationlog.entity.UserOperationLog;
import cn.mw.loganalysis.operationlog.mapper.UserOperationLogMapper;
import cn.mw.loganalysis.operationlog.service.OperationLogService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final OperationLogConverter operationLogConverter;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveLog(UserOperationLog operationLog) {
        log.info("========= [Service] saveLog called: user={}, action={}",
            operationLog.getUsername(), operationLog.getAction());
        try {
            operationLogMapper.insert(operationLog);
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
            operationLogMapper.insert(log);
        }
    }

    @Override
    public Page<OperationLogDTO> queryLogs(QueryOperationLogRequest request) {
        Page<UserOperationLog> page = new Page<>(request.getPageNum(), request.getPageSize());

        Page<UserOperationLog> resultPage = operationLogMapper.selectPageByCondition(
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
        UserOperationLog entity = operationLogMapper.selectById(id);
        return entity != null ? operationLogConverter.toDTO(entity) : null;
    }

    @Override
    public List<OperationLogDTO> getRecentLogsByUserId(Long userId, int limit) {
        List<UserOperationLog> logs = operationLogMapper.selectRecentByUserId(userId, limit);
        return operationLogConverter.toDTOList(logs);
    }

    @Override
    public List<OperationStatsDTO> statsByOperationType(String startTime, String endTime) {
        String sql = """
            SELECT
                operation_type AS name,
                COUNT(*) AS count,
                SUM(CASE WHEN is_success = true THEN 1 ELSE 0 END) AS success_count,
                SUM(CASE WHEN is_success = false THEN 1 ELSE 0 END) AS failure_count,
                ROUND(SUM(CASE WHEN is_success = true THEN 1 ELSE 0 END)::numeric / COUNT(*)::numeric * 100, 2) AS success_rate
            FROM user_operation_logs
            WHERE created_at >= ?::timestamp AND created_at <= ?::timestamp
            GROUP BY operation_type
            ORDER BY count DESC
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> OperationStatsDTO.builder()
            .name(rs.getString("name"))
            .count(rs.getLong("count"))
            .successCount(rs.getLong("success_count"))
            .failureCount(rs.getLong("failure_count"))
            .successRate(rs.getDouble("success_rate"))
            .build(), startTime, endTime);
    }

    @Override
    public List<OperationStatsDTO> statsByModule(String startTime, String endTime) {
        String sql = """
            SELECT
                module AS name,
                COUNT(*) AS count,
                SUM(CASE WHEN is_success = true THEN 1 ELSE 0 END) AS success_count,
                SUM(CASE WHEN is_success = false THEN 1 ELSE 0 END) AS failure_count,
                ROUND(SUM(CASE WHEN is_success = true THEN 1 ELSE 0 END)::numeric / COUNT(*)::numeric * 100, 2) AS success_rate
            FROM user_operation_logs
            WHERE created_at >= ?::timestamp AND created_at <= ?::timestamp
            GROUP BY module
            ORDER BY count DESC
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> OperationStatsDTO.builder()
            .name(rs.getString("name"))
            .count(rs.getLong("count"))
            .successCount(rs.getLong("success_count"))
            .failureCount(rs.getLong("failure_count"))
            .successRate(rs.getDouble("success_rate"))
            .build(), startTime, endTime);
    }

    @Override
    public List<OperationStatsDTO> statsByUser(String startTime, String endTime, int limit) {
        String sql = """
            SELECT
                username AS name,
                COUNT(*) AS count,
                SUM(CASE WHEN is_success = true THEN 1 ELSE 0 END) AS success_count,
                SUM(CASE WHEN is_success = false THEN 1 ELSE 0 END) AS failure_count,
                ROUND(SUM(CASE WHEN is_success = true THEN 1 ELSE 0 END)::numeric / COUNT(*)::numeric * 100, 2) AS success_rate
            FROM user_operation_logs
            WHERE created_at >= ?::timestamp AND created_at <= ?::timestamp
            GROUP BY username
            ORDER BY count DESC
            LIMIT ?
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> OperationStatsDTO.builder()
            .name(rs.getString("name"))
            .count(rs.getLong("count"))
            .successCount(rs.getLong("success_count"))
            .failureCount(rs.getLong("failure_count"))
            .successRate(rs.getDouble("success_rate"))
            .build(), startTime, endTime, limit);
    }

    @Override
    public Map<String, String> detectAnomalousOperations(Long userId, String ipAddress) {
        Map<String, String> alerts = new HashMap<>();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime fiveMinutesAgo = now.minusMinutes(5);

        // 检测 1: 高频失败 (5分钟内失败 > 20次)
        Long failureCount = operationLogMapper.countFailuresByUserIdAndTime(userId, fiveMinutesAgo, now);
        if (failureCount > 20) {
            alerts.put("HIGH_FREQUENCY_FAILURE",
                String.format("用户 %d 在5分钟内失败 %d 次，可能是密码爆破或系统异常", userId, failureCount));
        }

        // 检测 2: 异常 IP 登录 (新 IP 登录成功)
        boolean isNewIp = !operationLogMapper.existsByUserIdAndIp(userId, ipAddress);
        if (isNewIp) {
            alerts.put("NEW_IP_LOGIN",
                String.format("用户 %d 从新 IP %s 登录成功，请确认是否为本人操作", userId, ipAddress));
        }

        // 检测 3: 批量删除 (5分钟内删除 > 10次)
        String deleteSql = """
            SELECT COUNT(*) FROM user_operation_logs
            WHERE user_id = ? AND operation_type = 'DELETE'
            AND created_at >= ? AND created_at <= ?
            """;
        Long deleteCount = jdbcTemplate.queryForObject(deleteSql, Long.class, userId, fiveMinutesAgo, now);
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
            String insertSql = """
                INSERT INTO user_operation_logs_archive
                SELECT * FROM user_operation_logs
                WHERE created_at < ?
                """;
            int archivedCount = jdbcTemplate.update(insertSql, cutoffDate);

            // 2. 删除主表旧数据
            String deleteSql = "DELETE FROM user_operation_logs WHERE created_at < ?";
            jdbcTemplate.update(deleteSql, cutoffDate);

            log.info("Successfully archived {} operation logs before {}", archivedCount, cutoffDate);
            return archivedCount;
        } catch (Exception e) {
            log.error("Failed to archive operation logs", e);
            throw e;
        }
    }
}
