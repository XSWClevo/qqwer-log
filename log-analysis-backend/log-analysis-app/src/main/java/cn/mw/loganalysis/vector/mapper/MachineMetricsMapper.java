package cn.mw.loganalysis.vector.mapper;

import cn.mw.loganalysis.common.util.DateTimeUtils;
import cn.mw.loganalysis.vector.entity.MachineMetrics;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 机器指标 Mapper（ClickHouse）
 */
@Mapper
@DS("clickhouse")
public interface MachineMetricsMapper extends BaseMapper<MachineMetrics> {

    /**
     * 查询指定机器在时间范围内的指标
     */
    default List<MachineMetrics> selectByMachineIdAndTimeRange(
            String machineId, 
            LocalDateTime startTime, 
            LocalDateTime endTime) {
        LambdaQueryWrapper<MachineMetrics> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MachineMetrics::getMachineId, machineId)
               .ge(MachineMetrics::getCollectedAt, DateTimeUtils.format(startTime))
               .le(MachineMetrics::getCollectedAt, DateTimeUtils.format(endTime))
               .orderByAsc(MachineMetrics::getCollectedAt);
        return selectList(wrapper);
    }

    /**
     * 查询指定机器最近 N 条指标
     */
    default List<MachineMetrics> selectRecentByMachineId(String machineId, int limit) {
        LambdaQueryWrapper<MachineMetrics> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MachineMetrics::getMachineId, machineId)
               .orderByDesc(MachineMetrics::getCollectedAt)
               .last("LIMIT " + limit);
        return selectList(wrapper);
    }

    /**
     * 查询指定机器的最新一条指标
     */
    default MachineMetrics selectLatestByMachineId(String machineId) {
        LambdaQueryWrapper<MachineMetrics> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MachineMetrics::getMachineId, machineId)
               .orderByDesc(MachineMetrics::getCollectedAt)
               .last("LIMIT 1");
        return selectOne(wrapper);
    }

    /**
     * 批量插入指标
     * 注意：ClickHouse 的 MyBatis Plus 批量插入需要特殊处理
     */
    default void insertBatch(List<MachineMetrics> metricsList) {
        for (MachineMetrics metrics : metricsList) {
            insert(metrics);
        }
    }
}
