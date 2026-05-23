package cn.mw.loganalysis.attack.mapper;

import cn.mw.loganalysis.attack.entity.AttackDetectionRule;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@DS("postgres")
public interface AttackDetectionRuleMapper extends BaseMapper<AttackDetectionRule> {
}
