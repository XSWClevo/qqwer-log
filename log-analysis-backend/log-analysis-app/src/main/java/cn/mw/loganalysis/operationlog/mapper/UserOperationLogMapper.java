package cn.mw.loganalysis.operationlog.mapper;

import cn.mw.loganalysis.operationlog.entity.UserOperationLog;
import cn.mw.loganalysis.operationlog.enums.OperationType;
import cn.mw.loganalysis.operationlog.dto.response.OperationStatsDTO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.dynamic.datasource.annotation.DS;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;

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

    List<OperationStatsDTO> selectStatsByOperationType(@Param("startTime") String startTime,
                                                       @Param("endTime") String endTime);

    List<OperationStatsDTO> selectStatsByModule(@Param("startTime") String startTime,
                                                @Param("endTime") String endTime);

    List<OperationStatsDTO> selectStatsByUser(@Param("startTime") String startTime,
                                              @Param("endTime") String endTime,
                                              @Param("limit") int limit);

    Long countDeleteOperationsByUserIdAndTime(@Param("userId") Long userId,
                                              @Param("startTime") LocalDateTime startTime,
                                              @Param("endTime") LocalDateTime endTime);

    int insertArchiveBefore(@Param("cutoffDate") LocalDateTime cutoffDate);

    int deleteLogsBefore(@Param("cutoffDate") LocalDateTime cutoffDate);
}
