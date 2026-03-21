package cn.mw.loganalysis.operationlog.repository;

import cn.mw.loganalysis.operationlog.entity.UserOperationLog;
import cn.mw.loganalysis.operationlog.enums.OperationType;
import cn.mw.loganalysis.operationlog.mapper.UserOperationLogMapper;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 用户操作日志仓储。
 *
 * 只承接简单 CRUD、分页和条件统计。
 * 聚合报表、归档迁移这类明显自定义 SQL 继续保留在 Mapper/XML。
 */
@Repository
@DS("postgres")
@RequiredArgsConstructor
public class UserOperationLogRepository {

    private final UserOperationLogMapper userOperationLogMapper;

    public void save(UserOperationLog operationLog) {
        if (operationLog == null) {
            return;
        }
        userOperationLogMapper.insert(operationLog);
    }

    public UserOperationLog findById(Long id) {
        return id == null ? null : userOperationLogMapper.selectById(id);
    }

    public Page<UserOperationLog> findPageByCondition(Page<UserOperationLog> page,
                                                      Long userId,
                                                      String username,
                                                      OperationType operationType,
                                                      String module,
                                                      Boolean isSuccess,
                                                      String ipAddress,
                                                      LocalDateTime startTime,
                                                      LocalDateTime endTime) {
        return userOperationLogMapper.selectPage(
                page,
                Wrappers.<UserOperationLog>lambdaQuery()
                        .eq(ObjectUtils.isNotEmpty(userId), UserOperationLog::getUserId, userId)
                        .like(StringUtils.isNotBlank(username), UserOperationLog::getUsername, StringUtils.trim(username))
                        .eq(operationType != null, UserOperationLog::getOperationType, operationType)
                        .eq(StringUtils.isNotBlank(module), UserOperationLog::getModule, StringUtils.trim(module))
                        .eq(isSuccess != null, UserOperationLog::getIsSuccess, isSuccess)
                        .eq(StringUtils.isNotBlank(ipAddress), UserOperationLog::getIpAddress, StringUtils.trim(ipAddress))
                        .ge(startTime != null, UserOperationLog::getCreatedAt, startTime)
                        .le(endTime != null, UserOperationLog::getCreatedAt, endTime)
                        .orderByDesc(UserOperationLog::getCreatedAt)
        );
    }

    public List<UserOperationLog> findRecentByUserId(Long userId, int limit) {
        if (ObjectUtils.isEmpty(userId) || limit <= 0) {
            return Collections.emptyList();
        }

        return userOperationLogMapper.selectPage(
                new Page<>(1, limit, false),
                Wrappers.<UserOperationLog>lambdaQuery()
                        .eq(UserOperationLog::getUserId, userId)
                        .orderByDesc(UserOperationLog::getCreatedAt)
        ).getRecords();
    }

    public Long countFailuresByUserIdAndTime(Long userId, LocalDateTime startTime, LocalDateTime endTime) {
        if (ObjectUtils.isEmpty(userId) || startTime == null || endTime == null) {
            return 0L;
        }

        return userOperationLogMapper.selectCount(
                Wrappers.<UserOperationLog>lambdaQuery()
                        .eq(UserOperationLog::getUserId, userId)
                        .eq(UserOperationLog::getIsSuccess, false)
                        .ge(UserOperationLog::getCreatedAt, startTime)
                        .le(UserOperationLog::getCreatedAt, endTime)
        );
    }

    public Long countFailuresByIpAndTime(String ipAddress, LocalDateTime startTime, LocalDateTime endTime) {
        if (StringUtils.isBlank(ipAddress) || startTime == null || endTime == null) {
            return 0L;
        }

        return userOperationLogMapper.selectCount(
                Wrappers.<UserOperationLog>lambdaQuery()
                        .eq(UserOperationLog::getIpAddress, StringUtils.trim(ipAddress))
                        .eq(UserOperationLog::getIsSuccess, false)
                        .ge(UserOperationLog::getCreatedAt, startTime)
                        .le(UserOperationLog::getCreatedAt, endTime)
        );
    }

    public boolean existsByUserIdAndIp(Long userId, String ipAddress) {
        if (ObjectUtils.isEmpty(userId) || StringUtils.isBlank(ipAddress)) {
            return false;
        }

        return userOperationLogMapper.selectCount(
                Wrappers.<UserOperationLog>lambdaQuery()
                        .eq(UserOperationLog::getUserId, userId)
                        .eq(UserOperationLog::getIpAddress, StringUtils.trim(ipAddress))
        ) > 0;
    }
}
