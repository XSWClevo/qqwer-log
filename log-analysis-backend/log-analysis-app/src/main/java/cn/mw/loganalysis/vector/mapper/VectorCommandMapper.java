package cn.mw.loganalysis.vector.mapper;

import cn.mw.loganalysis.vector.entity.VectorCommand;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
@DS("postgres")
public interface VectorCommandMapper extends BaseMapper<VectorCommand> {

    /**
     * 获取待执行的命令
     *
     * @param machineId 机器ID
     * @param status 命令状态
     * @return 最早创建的待执行命令
     */
    default VectorCommand selectPendingCommand(String machineId, String status) {
        LambdaQueryWrapper<VectorCommand> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VectorCommand::getMachineId, machineId)
               .eq(VectorCommand::getStatus, status)
               .orderByAsc(VectorCommand::getCreatedAt)
               .last("LIMIT 1");
        return selectOne(wrapper);
    }

    /**
     * 分页查询机器的命令历史
     *
     * @param page 分页参数
     * @param machineId 机器ID
     * @return 分页结果
     */
    default Page<VectorCommand> selectPageByMachineId(Page<VectorCommand> page, String machineId) {
        LambdaQueryWrapper<VectorCommand> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VectorCommand::getMachineId, machineId)
               .orderByDesc(VectorCommand::getCreatedAt);
        return selectPage(page, wrapper);
    }

    /**
     * 获取最近的命令列表
     *
     * @param machineId 机器ID
     * @param limit 限制数量
     * @return 命令列表
     */
    default List<VectorCommand> selectRecentCommands(String machineId, int limit) {
        LambdaQueryWrapper<VectorCommand> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VectorCommand::getMachineId, machineId)
               .orderByDesc(VectorCommand::getCreatedAt)
               .last("LIMIT " + limit);
        return selectList(wrapper);
    }
}
