package cn.mw.loganalysis.vector.mapper;

import cn.mw.loganalysis.vector.entity.MachineConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 机器配置关系 Mapper
 */
@Mapper
public interface MachineConfigMapper extends BaseMapper<MachineConfig> {

    /**
     * 根据机器ID查询已部署的配置
     */
    default List<MachineConfig> selectByMachineId(String machineId) {
        LambdaQueryWrapper<MachineConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MachineConfig::getMachineId, machineId)
               .orderByDesc(MachineConfig::getCreatedAt);
        return selectList(wrapper);
    }

    /**
     * 根据配置ID查询部署到哪些机器
     */
    default List<MachineConfig> selectByConfigId(String configId) {
        LambdaQueryWrapper<MachineConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MachineConfig::getConfigId, configId);
        return selectList(wrapper);
    }

    /**
     * 查询机器上某个配置的部署状态
     */
    default MachineConfig selectByMachineAndConfig(String machineId, String configId) {
        LambdaQueryWrapper<MachineConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MachineConfig::getMachineId, machineId)
               .eq(MachineConfig::getConfigId, configId);
        return selectOne(wrapper);
    }

    /**
     * 查询机器上已部署或待部署的配置详情
     * 包含 pending 和 deployed 状态，确保新部署的配置也能被合并
     */
    @Select("SELECT vc.* FROM vector_visual_configs vc " +
            "JOIN vector_machine_configs mc ON vc.id = mc.config_id " +
            "WHERE mc.machine_id = #{machineId} AND mc.status IN ('deployed', 'pending')")
    List<cn.mw.loganalysis.vector.entity.VisualConfig> selectDeployedConfigsByMachineId(@Param("machineId") String machineId);
}
