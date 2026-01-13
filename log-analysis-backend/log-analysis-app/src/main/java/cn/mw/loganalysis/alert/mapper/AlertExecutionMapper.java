package cn.mw.loganalysis.alert.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 告警执行 Mapper
 * 用于执行动态 SQL 查询 ClickHouse
 * 注意: 数据源通过调用方的 @DS 注解控制
 */
@Mapper
public interface AlertExecutionMapper {

    /**
     * 执行动态查询
     */
    List<Map<String, Object>> executeDynamicQuery(@Param("sql") String sql);
}
