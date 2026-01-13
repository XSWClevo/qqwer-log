package cn.mw.loganalysis.vector.mapper;

import cn.mw.loganalysis.vector.entity.ConfigComponent;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

import java.util.List;

@Mapper
@DS("postgres")
public interface ConfigComponentMapper extends BaseMapper<ConfigComponent> {

    default List<ConfigComponent> selectByCondition(String keyword, String componentType) {
        LambdaQueryWrapper<ConfigComponent> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(StringUtils.hasText(keyword), w -> w
                .like(ConfigComponent::getName, keyword)
                .or()
                .like(ConfigComponent::getDescription, keyword))
               .eq(StringUtils.hasText(componentType), ConfigComponent::getComponentType, componentType)
               .orderByDesc(ConfigComponent::getUpdatedAt);
        return selectList(wrapper);
    }

    /**
     * 查询可作为数据源的 Sink 组件（queryable=true）
     */
    default List<ConfigComponent> selectQueryableSinks() {
        LambdaQueryWrapper<ConfigComponent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ConfigComponent::getComponentType, "sink")
               .eq(ConfigComponent::getQueryable, true)
               .orderByDesc(ConfigComponent::getUpdatedAt);
        return selectList(wrapper);
    }
}
