package cn.mw.loganalysis.config.mapper;

import cn.mw.loganalysis.config.entity.ConfigHistory;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 配置历史Mapper
 * 使用PostgreSQL数据源存储配置变更历史
 */
@Mapper
@DS("postgres")
public interface ConfigHistoryMapper extends BaseMapper<ConfigHistory> {
}
