package cn.mw.loganalysis.vector.mapper;

import cn.mw.loganalysis.vector.entity.VectorDeployment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Vector部署记录Mapper
 */
@Mapper
public interface VectorDeploymentMapper extends BaseMapper<VectorDeployment> {

    /**
     * 根据机器ID查询部署记录
     */
    @Select("SELECT * FROM vector_deployments WHERE machine_id = #{machineId} ORDER BY created_at DESC")
    List<VectorDeployment> selectByMachineId(@Param("machineId") String machineId);

    /**
     * 根据配置ID查询部署记录
     */
    @Select("SELECT * FROM vector_deployments WHERE config_id = #{configId} ORDER BY created_at DESC")
    List<VectorDeployment> selectByConfigId(@Param("configId") String configId);

    /**
     * 根据配置版本查询部署记录
     */
    @Select("SELECT * FROM vector_deployments WHERE config_version = #{configVersion} ORDER BY created_at DESC")
    List<VectorDeployment> selectByConfigVersion(@Param("configVersion") String configVersion);

    /**
     * 查询机器最新的待部署配置
     */
    @Select("SELECT * FROM vector_deployments WHERE machine_id = #{machineId} AND status = 'pending' ORDER BY created_at DESC LIMIT 1")
    VectorDeployment selectLatestPendingByMachineId(@Param("machineId") String machineId);

    /**
     * 分页查询部署记录
     */
    @Select("<script>" +
            "SELECT * FROM vector_deployments " +
            "<where>" +
            "  <if test='machineId != null'>AND machine_id = #{machineId}</if>" +
            "  <if test='configId != null'>AND config_id = #{configId}</if>" +
            "  <if test='status != null'>AND status = #{status}</if>" +
            "</where>" +
            "ORDER BY created_at DESC" +
            "</script>")
    Page<VectorDeployment> selectPageByCondition(Page<VectorDeployment> page,
                                                  @Param("machineId") String machineId,
                                                  @Param("configId") String configId,
                                                  @Param("status") String status);
}
