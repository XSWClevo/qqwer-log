package cn.mw.loganalysis.config.mapper;

import cn.mw.loganalysis.config.entity.UserFieldConfig;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户字段配置Mapper
 * 使用PostgreSQL数据源存储用户字段配置
 */
@Mapper
@DS("postgres")
public interface UserFieldConfigMapper extends BaseMapper<UserFieldConfig> {
}
