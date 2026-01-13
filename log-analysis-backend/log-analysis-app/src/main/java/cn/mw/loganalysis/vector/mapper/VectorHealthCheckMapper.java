package cn.mw.loganalysis.vector.mapper;

import cn.mw.loganalysis.vector.entity.VectorHealthCheck;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Vector健康检查 Mapper
 */
@Mapper
@DS("postgres")
public interface VectorHealthCheckMapper extends BaseMapper<VectorHealthCheck> {

    /**
     * 根据机器ID查询健康检查记录
     */
    default List<VectorHealthCheck> selectByMachineId(String machineId, Integer limit) {
        LambdaQueryWrapper<VectorHealthCheck> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VectorHealthCheck::getMachineId, machineId)
               .orderByDesc(VectorHealthCheck::getCheckedAt);
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        return selectList(wrapper);
    }

    /**
     * 根据检查类型和状态查询
     */
    default List<VectorHealthCheck> selectByTypeAndStatus(String checkType,
                                                           String status,
                                                           LocalDateTime startTime,
                                                           Integer limit) {
        LambdaQueryWrapper<VectorHealthCheck> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(checkType), VectorHealthCheck::getCheckType, checkType)
               .eq(StringUtils.hasText(status), VectorHealthCheck::getStatus, status)
               .ge(startTime != null, VectorHealthCheck::getCheckedAt, startTime)
               .orderByDesc(VectorHealthCheck::getCheckedAt);
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        return selectList(wrapper);
    }
}
