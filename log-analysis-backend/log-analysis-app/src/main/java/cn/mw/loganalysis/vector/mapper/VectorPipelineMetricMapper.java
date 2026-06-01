package cn.mw.loganalysis.vector.mapper;

import cn.mw.loganalysis.common.util.DateTimeUtils;
import cn.mw.loganalysis.vector.entity.VectorPipelineMetric;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Vector管道指标 Mapper
 */
@Mapper
@DS("clickhouse")
public interface VectorPipelineMetricMapper extends BaseMapper<VectorPipelineMetric> {

    /**
     * 根据机器ID和时间范围查询指标
     */
    default List<VectorPipelineMetric> selectByMachineAndTimeRange(String machineId,
                                                                     LocalDateTime startTime,
                                                                     LocalDateTime endTime,
                                                                     Integer limit) {
        LambdaQueryWrapper<VectorPipelineMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.isNotBlank(machineId), VectorPipelineMetric::getMachineId, machineId)
               .ge(startTime != null, VectorPipelineMetric::getRecordedAt, DateTimeUtils.format(startTime))
               .le(endTime != null, VectorPipelineMetric::getRecordedAt, DateTimeUtils.format(endTime))
               .orderByDesc(VectorPipelineMetric::getRecordedAt);
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        return selectList(wrapper);
    }

    /**
     * 根据Source名称查询指标
     */
    default List<VectorPipelineMetric> selectBySource(String sourceName, Integer limit) {
        LambdaQueryWrapper<VectorPipelineMetric> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VectorPipelineMetric::getSourceName, sourceName)
               .orderByDesc(VectorPipelineMetric::getRecordedAt);
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        return selectList(wrapper);
    }
}
