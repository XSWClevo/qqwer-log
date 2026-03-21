package cn.mw.loganalysis.datasource.mapper;

import cn.mw.loganalysis.datasource.entity.Datasource;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据源 Mapper
 */
@Mapper
@DS("postgres")
public interface DatasourceMapper extends BaseMapper<Datasource> {
}
