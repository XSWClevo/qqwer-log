package cn.mw.loganalysis.datasource.mapper;

import cn.mw.loganalysis.datasource.entity.Datasource;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 数据源 Mapper
 */
@Mapper
@DS("postgres")
public interface DatasourceMapper extends BaseMapper<Datasource> {

    /**
     * 根据条件查询数据源列表
     */
    default List<Datasource> selectByCondition(String name, String type, String status) {
        LambdaQueryWrapper<Datasource> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(name), Datasource::getName, name)
               .eq(StringUtils.hasText(type), Datasource::getType, type)
               .eq(StringUtils.hasText(status), Datasource::getStatus, status)
               .orderByDesc(Datasource::getCreatedAt);
        return selectList(wrapper);
    }

    /**
     * 分页查询数据源
     */
    default Page<Datasource> selectPageByCondition(Page<Datasource> page, String keyword, String type, String status) {
        LambdaQueryWrapper<Datasource> wrapper = new LambdaQueryWrapper<>();

        // 关键词搜索（名称或描述）
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(Datasource::getName, keyword)
                    .or()
                    .like(Datasource::getDescription, keyword));
        }

        // 类型筛选
        wrapper.eq(StringUtils.hasText(type), Datasource::getType, type);

        // 状态筛选
        wrapper.eq(StringUtils.hasText(status), Datasource::getStatus, status);

        // 按创建时间倒序
        wrapper.orderByDesc(Datasource::getCreatedAt);

        return selectPage(page, wrapper);
    }

    /**
     * 根据名称查询数据源
     */
    default Datasource selectByName(String name) {
        LambdaQueryWrapper<Datasource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Datasource::getName, name);
        return selectOne(wrapper);
    }

    /**
     * 根据类型查询数据源列表
     */
    default List<Datasource> selectByType(String type) {
        LambdaQueryWrapper<Datasource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Datasource::getType, type)
               .eq(Datasource::getStatus, "active")
               .orderByDesc(Datasource::getCreatedAt);
        return selectList(wrapper);
    }

    /**
     * 查询所有活跃的数据源
     */
    default List<Datasource> selectActive() {
        LambdaQueryWrapper<Datasource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Datasource::getStatus, "active")
               .orderByDesc(Datasource::getCreatedAt);
        return selectList(wrapper);
    }
}
