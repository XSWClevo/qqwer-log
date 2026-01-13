package cn.mw.loganalysis.vector.mapper;

import cn.mw.loganalysis.vector.entity.VectorServiceOperation;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Vector服务操作 Mapper
 */
@Mapper
@DS("postgres")
public interface VectorServiceOperationMapper extends BaseMapper<VectorServiceOperation> {

    /**
     * 根据机器ID查询操作记录
     */
    default List<VectorServiceOperation> selectByMachineId(String machineId, Integer limit) {
        LambdaQueryWrapper<VectorServiceOperation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VectorServiceOperation::getMachineId, machineId)
               .orderByDesc(VectorServiceOperation::getCreatedAt);
        if (limit != null && limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        return selectList(wrapper);
    }

    /**
     * 分页查询操作记录
     */
    default Page<VectorServiceOperation> selectPageByCondition(Page<VectorServiceOperation> page,
                                                                String machineId,
                                                                String operationType,
                                                                String status) {
        LambdaQueryWrapper<VectorServiceOperation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(machineId), VectorServiceOperation::getMachineId, machineId)
               .eq(StringUtils.hasText(operationType), VectorServiceOperation::getOperationType, operationType)
               .eq(StringUtils.hasText(status), VectorServiceOperation::getStatus, status)
               .orderByDesc(VectorServiceOperation::getCreatedAt);
        return selectPage(page, wrapper);
    }
}
