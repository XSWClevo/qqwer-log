package cn.mw.loganalysis.vector.mapper;

import cn.mw.loganalysis.vector.entity.VectorDeployment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Vector部署记录Mapper
 */
@Mapper
public interface VectorDeploymentMapper extends BaseMapper<VectorDeployment> {

    /**
     * 根据机器ID查询部署记录
     */
    List<VectorDeployment> selectByMachineId(@Param("machineId") String machineId);

    /**
     * 根据配置ID查询部署记录
     */
    List<VectorDeployment> selectByConfigId(@Param("configId") String configId);

    /**
     * 根据配置版本查询部署记录
     */
    List<VectorDeployment> selectByConfigVersion(@Param("configVersion") String configVersion);

    /**
     * 查询机器最新的待部署配置
     */
    VectorDeployment selectLatestPendingByMachineId(@Param("machineId") String machineId);

    /**
     * 分页查询部署记录
     */
    Page<VectorDeployment> selectPageByCondition(Page<VectorDeployment> page,
                                                  @Param("machineId") String machineId,
                                                  @Param("configId") String configId,
                                                  @Param("status") String status);
}
