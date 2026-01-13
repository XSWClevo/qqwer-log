package cn.mw.loganalysis.vector.mapper;

import cn.mw.loganalysis.vector.entity.VectorConfig;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Vector配置管理 Mapper
 */
@Mapper
@DS("postgres")
public interface VectorConfigMapper extends BaseMapper<VectorConfig> {

    /**
     * 分页查询配置列表
     */
    default Page<VectorConfig> selectPageByCondition(Page<VectorConfig> page, String keyword, Boolean isTemplate) {
        LambdaQueryWrapper<VectorConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(StringUtils.hasText(keyword), w -> w
                .like(VectorConfig::getName, keyword)
                .or()
                .like(VectorConfig::getDescription, keyword))
               .eq(isTemplate != null, VectorConfig::getIsTemplate, isTemplate)
               .orderByDesc(VectorConfig::getUpdatedAt);
        return selectPage(page, wrapper);
    }

    /**
     * 查询模板配置列表
     */
    default List<VectorConfig> selectTemplates() {
        LambdaQueryWrapper<VectorConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VectorConfig::getIsTemplate, true)
               .orderByDesc(VectorConfig::getCreatedAt);
        return selectList(wrapper);
    }

    /**
     * 查询最新版本配置
     */
    default VectorConfig selectLatestVersion(String name) {
        LambdaQueryWrapper<VectorConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VectorConfig::getName, name)
               .orderByDesc(VectorConfig::getVersion)
               .last("LIMIT 1");
        return selectOne(wrapper);
    }
}
