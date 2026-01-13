package cn.mw.loganalysis.extraction.mapper;

import cn.mw.loganalysis.extraction.entity.ExtractionRule;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提取规则Mapper
 * 使用PostgreSQL数据源存储提取规则配置
 */
@Mapper
@DS("postgres")
public interface ExtractionRuleMapper extends BaseMapper<ExtractionRule> {
}
