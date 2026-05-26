package cn.mw.loganalysis.alert.mapper;

import cn.mw.loganalysis.alert.entity.AlertEvaluationRun;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@DS("postgres")
public interface AlertEvaluationRunMapper extends BaseMapper<AlertEvaluationRun> {
}
