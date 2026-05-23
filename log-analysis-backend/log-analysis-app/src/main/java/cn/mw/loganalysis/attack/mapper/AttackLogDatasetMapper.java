package cn.mw.loganalysis.attack.mapper;

import cn.mw.loganalysis.attack.entity.AttackLogDataset;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@DS("postgres")
public interface AttackLogDatasetMapper extends BaseMapper<AttackLogDataset> {
}
