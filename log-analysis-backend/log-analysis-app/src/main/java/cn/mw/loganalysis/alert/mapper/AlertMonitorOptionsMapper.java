package cn.mw.loganalysis.alert.mapper;

import cn.mw.loganalysis.alert.entity.AlertMonitorOptions;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@DS("postgres")
public interface AlertMonitorOptionsMapper extends BaseMapper<AlertMonitorOptions> {
}
