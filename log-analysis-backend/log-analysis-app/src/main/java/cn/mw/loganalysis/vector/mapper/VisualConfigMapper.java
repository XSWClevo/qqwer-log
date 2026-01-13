package cn.mw.loganalysis.vector.mapper;

import cn.mw.loganalysis.vector.entity.VisualConfig;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 可视化配置 Mapper
 */
@Mapper
@DS("postgres")
public interface VisualConfigMapper extends BaseMapper<VisualConfig> {

    /**
     * 查询配置列表
     */
    default List<VisualConfig> selectByCondition(String keyword) {
        LambdaQueryWrapper<VisualConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(StringUtils.hasText(keyword), w -> w
                .like(VisualConfig::getName, keyword)
                .or()
                .like(VisualConfig::getDescription, keyword))
               .orderByDesc(VisualConfig::getUpdatedAt);
        return selectList(wrapper);
    }
}
