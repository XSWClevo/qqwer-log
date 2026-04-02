package cn.mw.loganalysis.logcategory.mapper;

import cn.mw.loganalysis.logcategory.entity.LogCategoryRegistry;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 日志分类注册表 Mapper
 */
@Mapper
@DS("postgres")
public interface LogCategoryRegistryMapper extends BaseMapper<LogCategoryRegistry> {

    default List<LogCategoryRegistry> selectEnabledByCategoryCodes(List<String> categoryCodes) {
        LambdaQueryWrapper<LogCategoryRegistry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LogCategoryRegistry::getEnabled, true);
        if (CollectionUtils.isNotEmpty(categoryCodes)) {
            wrapper.in(LogCategoryRegistry::getCategoryCode, categoryCodes);
        }
        wrapper.orderByAsc(LogCategoryRegistry::getPriority)
                .orderByAsc(LogCategoryRegistry::getId);
        return selectList(wrapper);
    }

    default List<LogCategoryRegistry> selectEnabledByDatasourceIds(List<String> datasourceIds) {
        LambdaQueryWrapper<LogCategoryRegistry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LogCategoryRegistry::getEnabled, true);
        if (CollectionUtils.isNotEmpty(datasourceIds)) {
            wrapper.in(LogCategoryRegistry::getDatasourceId, datasourceIds);
        }
        wrapper.orderByAsc(LogCategoryRegistry::getPriority)
                .orderByAsc(LogCategoryRegistry::getId);
        return selectList(wrapper);
    }

    default List<LogCategoryRegistry> selectEnabledByTableNames(List<String> tableNames) {
        LambdaQueryWrapper<LogCategoryRegistry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LogCategoryRegistry::getEnabled, true);
        if (CollectionUtils.isNotEmpty(tableNames)) {
            wrapper.in(LogCategoryRegistry::getTableName, tableNames);
        }
        wrapper.orderByAsc(LogCategoryRegistry::getPriority)
                .orderByAsc(LogCategoryRegistry::getId);
        return selectList(wrapper);
    }

    default LogCategoryRegistry selectEnabledByDatasourceAndTable(String datasourceId, String tableName) {
        LambdaQueryWrapper<LogCategoryRegistry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LogCategoryRegistry::getEnabled, true)
                .eq(StringUtils.isNotBlank(datasourceId), LogCategoryRegistry::getDatasourceId, datasourceId)
                .eq(LogCategoryRegistry::getTableName, tableName)
                .last("LIMIT 1");
        return selectOne(wrapper);
    }
}
