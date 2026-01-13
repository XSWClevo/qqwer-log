package cn.mw.loganalysis.operationlog.mapper;

import cn.mw.loganalysis.operationlog.entity.UserOperationLog;
import cn.mw.loganalysis.operationlog.enums.OperationType;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.dynamic.datasource.annotation.DS;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户操作日志 Mapper
 *
 * @author Claude
 * @since 2026-01-07
 */
@Mapper
@DS("postgres")
public interface UserOperationLogMapper extends BaseMapper<UserOperationLog> {

    /**
     * 分页查询操作日志
     *
     * @param page 分页对象
     * @param userId 用户ID
     * @param username 用户名
     * @param operationType 操作类型
     * @param module 模块
     * @param isSuccess 是否成功
     * @param ipAddress IP地址
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 分页结果
     */
    default Page<UserOperationLog> selectPageByCondition(
        Page<UserOperationLog> page,
        Long userId,
        String username,
        OperationType operationType,
        String module,
        Boolean isSuccess,
        String ipAddress,
        LocalDateTime startTime,
        LocalDateTime endTime
    ) {
        LambdaQueryWrapper<UserOperationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(userId != null, UserOperationLog::getUserId, userId)
            .like(StringUtils.hasText(username), UserOperationLog::getUsername, username)
            .eq(operationType != null, UserOperationLog::getOperationType, operationType)
            .eq(StringUtils.hasText(module), UserOperationLog::getModule, module)
            .eq(isSuccess != null, UserOperationLog::getIsSuccess, isSuccess)
            .eq(StringUtils.hasText(ipAddress), UserOperationLog::getIpAddress, ipAddress)
            .ge(startTime != null, UserOperationLog::getCreatedAt, startTime)
            .le(endTime != null, UserOperationLog::getCreatedAt, endTime)
            .orderByDesc(UserOperationLog::getCreatedAt);

        return selectPage(page, wrapper);
    }

    /**
     * 查询某用户最近 N 条操作日志
     *
     * @param userId 用户ID
     * @param limit 数量限制
     * @return 操作日志列表
     */
    default List<UserOperationLog> selectRecentByUserId(Long userId, int limit) {
        LambdaQueryWrapper<UserOperationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserOperationLog::getUserId, userId)
            .orderByDesc(UserOperationLog::getCreatedAt)
            .last("LIMIT " + limit);

        return selectList(wrapper);
    }

    /**
     * 统计某用户在指定时间范围内的失败次数 (用于告警检测)
     *
     * @param userId 用户ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 失败次数
     */
    default Long countFailuresByUserIdAndTime(Long userId, LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<UserOperationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserOperationLog::getUserId, userId)
            .eq(UserOperationLog::getIsSuccess, false)
            .ge(UserOperationLog::getCreatedAt, startTime)
            .le(UserOperationLog::getCreatedAt, endTime);

        return selectCount(wrapper);
    }

    /**
     * 统计某 IP 在指定时间范围内的失败次数 (用于告警检测)
     *
     * @param ipAddress IP地址
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 失败次数
     */
    default Long countFailuresByIpAndTime(String ipAddress, LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<UserOperationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserOperationLog::getIpAddress, ipAddress)
            .eq(UserOperationLog::getIsSuccess, false)
            .ge(UserOperationLog::getCreatedAt, startTime)
            .le(UserOperationLog::getCreatedAt, endTime);

        return selectCount(wrapper);
    }

    /**
     * 查询某用户是否曾使用过某 IP (用于新 IP 告警检测)
     *
     * @param userId 用户ID
     * @param ipAddress IP地址
     * @return 是否存在记录
     */
    default boolean existsByUserIdAndIp(Long userId, String ipAddress) {
        LambdaQueryWrapper<UserOperationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserOperationLog::getUserId, userId)
            .eq(UserOperationLog::getIpAddress, ipAddress)
            .last("LIMIT 1");

        return selectCount(wrapper) > 0;
    }

    /**
     * 统计按操作类型分组
     *
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 统计结果 (需要在 Service 层使用原生 SQL 实现)
     */
    default List<UserOperationLog> selectGroupByOperationType(LocalDateTime startTime, LocalDateTime endTime) {
        // 此方法需要在 Service 层使用原生 SQL 或聚合查询实现
        // 这里仅提供接口定义
        throw new UnsupportedOperationException("请在 Service 层使用原生 SQL 实现统计查询");
    }

    /**
     * 归档旧数据到归档表 (需要在 Service 层实现)
     *
     * @param cutoffDate 截止日期 (早于此日期的数据将被归档)
     * @return 归档的记录数
     */
    default int archiveOldLogs(LocalDateTime cutoffDate) {
        // 此方法需要在 Service 层使用原生 SQL 实现
        // INSERT INTO user_operation_logs_archive SELECT * FROM user_operation_logs WHERE created_at < ?
        // DELETE FROM user_operation_logs WHERE created_at < ?
        throw new UnsupportedOperationException("请在 Service 层使用原生 SQL 实现归档功能");
    }
}
