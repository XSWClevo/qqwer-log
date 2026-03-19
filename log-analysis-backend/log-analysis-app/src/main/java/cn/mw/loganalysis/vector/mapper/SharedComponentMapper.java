package cn.mw.loganalysis.vector.mapper;

import cn.mw.loganalysis.vector.entity.SharedComponent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 共享组件 Mapper
 */
@Mapper
public interface SharedComponentMapper extends BaseMapper<SharedComponent> {

    /**
     * 根据组件类型查询
     */
    default List<SharedComponent> selectByType(String componentType) {
        LambdaQueryWrapper<SharedComponent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(componentType), SharedComponent::getComponentType, componentType)
               .eq(SharedComponent::getIsActive, true)
               .orderByDesc(SharedComponent::getCreatedAt);
        return selectList(wrapper);
    }

    /**
     * 根据 componentKey 查询
     */
    default SharedComponent selectByKey(String componentKey) {
        LambdaQueryWrapper<SharedComponent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SharedComponent::getComponentKey, componentKey);
        return selectOne(wrapper);
    }

    /**
     * 查询组件被引用的次数
     */
    int countReferences(@Param("componentId") String componentId);

    /**
     * 查询引用该组件的配置名称
     */
    List<String> selectReferencingConfigNames(@Param("componentId") String componentId);
}
