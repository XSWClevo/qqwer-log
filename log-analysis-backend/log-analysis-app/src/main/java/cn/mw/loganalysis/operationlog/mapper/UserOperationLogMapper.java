package cn.mw.loganalysis.operationlog.mapper;

import cn.mw.loganalysis.operationlog.entity.UserOperationLog;
import cn.mw.loganalysis.operationlog.enums.OperationType;
import cn.mw.loganalysis.operationlog.dto.response.OperationStatsDTO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.dynamic.datasource.annotation.DS;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
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

    @Select("""
            SELECT
                operation_type AS name,
                COUNT(*) AS count,
                SUM(CASE WHEN is_success = true THEN 1 ELSE 0 END) AS success_count,
                SUM(CASE WHEN is_success = false THEN 1 ELSE 0 END) AS failure_count,
                ROUND(SUM(CASE WHEN is_success = true THEN 1 ELSE 0 END)::numeric / COUNT(*)::numeric * 100, 2) AS success_rate
            FROM user_operation_logs
            WHERE created_at >= #{startTime}::timestamp AND created_at <= #{endTime}::timestamp
            GROUP BY operation_type
            ORDER BY count DESC
            """)
    List<OperationStatsDTO> selectStatsByOperationType(@Param("startTime") String startTime,
                                                       @Param("endTime") String endTime);

    @Select("""
            SELECT
                module AS name,
                COUNT(*) AS count,
                SUM(CASE WHEN is_success = true THEN 1 ELSE 0 END) AS success_count,
                SUM(CASE WHEN is_success = false THEN 1 ELSE 0 END) AS failure_count,
                ROUND(SUM(CASE WHEN is_success = true THEN 1 ELSE 0 END)::numeric / COUNT(*)::numeric * 100, 2) AS success_rate
            FROM user_operation_logs
            WHERE created_at >= #{startTime}::timestamp AND created_at <= #{endTime}::timestamp
            GROUP BY module
            ORDER BY count DESC
            """)
    List<OperationStatsDTO> selectStatsByModule(@Param("startTime") String startTime,
                                                @Param("endTime") String endTime);

    @Select("""
            SELECT
                username AS name,
                COUNT(*) AS count,
                SUM(CASE WHEN is_success = true THEN 1 ELSE 0 END) AS success_count,
                SUM(CASE WHEN is_success = false THEN 1 ELSE 0 END) AS failure_count,
                ROUND(SUM(CASE WHEN is_success = true THEN 1 ELSE 0 END)::numeric / COUNT(*)::numeric * 100, 2) AS success_rate
            FROM user_operation_logs
            WHERE created_at >= #{startTime}::timestamp AND created_at <= #{endTime}::timestamp
            GROUP BY username
            ORDER BY count DESC
            LIMIT #{limit}
            """)
    List<OperationStatsDTO> selectStatsByUser(@Param("startTime") String startTime,
                                              @Param("endTime") String endTime,
                                              @Param("limit") int limit);

    @Select("""
            SELECT COUNT(*)
            FROM user_operation_logs
            WHERE user_id = #{userId}
              AND operation_type = 'DELETE'
              AND created_at >= #{startTime}
              AND created_at <= #{endTime}
            """)
    Long countDeleteOperationsByUserIdAndTime(@Param("userId") Long userId,
                                              @Param("startTime") LocalDateTime startTime,
                                              @Param("endTime") LocalDateTime endTime);

    @Insert("""
            INSERT INTO user_operation_logs_archive
            SELECT *
            FROM user_operation_logs
            WHERE created_at < #{cutoffDate}
            """)
    int insertArchiveBefore(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Delete("""
            DELETE FROM user_operation_logs
            WHERE created_at < #{cutoffDate}
            """)
    int deleteLogsBefore(@Param("cutoffDate") LocalDateTime cutoffDate);
}
