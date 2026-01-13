package cn.mw.loganalysis.vector.mapper;

import cn.mw.loganalysis.vector.entity.ConfigComponentRef;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 配置组件引用关系 Mapper
 */
@Mapper
public interface ConfigComponentRefMapper extends BaseMapper<ConfigComponentRef> {

    /**
     * 根据配置ID查询引用的共享组件
     */
    default List<ConfigComponentRef> selectByConfigId(String configId) {
        LambdaQueryWrapper<ConfigComponentRef> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConfigComponentRef::getConfigId, configId);
        return selectList(wrapper);
    }

    /**
     * 根据共享组件ID查询引用它的配置
     */
    default List<ConfigComponentRef> selectByComponentId(String componentId) {
        LambdaQueryWrapper<ConfigComponentRef> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConfigComponentRef::getSharedComponentId, componentId);
        return selectList(wrapper);
    }

    /**
     * 删除配置的所有引用
     */
    default int deleteByConfigId(String configId) {
        LambdaQueryWrapper<ConfigComponentRef> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConfigComponentRef::getConfigId, configId);
        return delete(wrapper);
    }

    /**
     * 查询配置引用的共享组件详情
     */
    @Select("SELECT sc.* FROM vector_shared_components sc " +
            "JOIN vector_config_component_refs cr ON sc.id = cr.shared_component_id " +
            "WHERE cr.config_id = #{configId}")
    List<cn.mw.loganalysis.vector.entity.SharedComponent> selectSharedComponentsByConfigId(@Param("configId") String configId);
}
