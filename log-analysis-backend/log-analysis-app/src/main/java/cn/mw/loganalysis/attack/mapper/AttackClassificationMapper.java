package cn.mw.loganalysis.attack.mapper;

import cn.mw.loganalysis.attack.entity.AttackClassificationRecord;
import cn.mw.loganalysis.attack.mapper.param.AttackDatasetScanSqlParam;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
@DS("clickhouse")
public interface AttackClassificationMapper extends BaseMapper<AttackClassificationRecord> {

    List<Map<String, Object>> selectDatasetLogs(AttackDatasetScanSqlParam param);
}
