package cn.mw.loganalysis.stats.mapper;

import cn.mw.loganalysis.stats.mapper.param.ContextLogQueryParam;
import cn.mw.loganalysis.stats.mapper.param.DimensionStatsQueryParam;
import cn.mw.loganalysis.stats.mapper.param.LogQuerySqlParam;
import cn.mw.loganalysis.stats.mapper.param.TimeSeriesQueryParam;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * PostgreSQL 查询 Mapper
 */
@Mapper
public interface PostgreSqlQueryMapper {

    String selectVersion();

    Long countTables(@Param("tableName") String tableName);

    List<Map<String, Object>> selectTableSchemaRows(@Param("tableName") String tableName);

    Long countTableRows(@Param("tableName") String tableName);

    List<String> selectTables();

    List<Map<String, Object>> selectLogs(LogQuerySqlParam param);

    Long countLogs(LogQuerySqlParam param);

    List<Map<String, Object>> selectContextBeforeLogs(ContextLogQueryParam param);

    List<Map<String, Object>> selectContextAfterLogs(ContextLogQueryParam param);

    List<Map<String, Object>> selectDimensionStats(DimensionStatsQueryParam param);

    List<Map<String, Object>> selectTimeSeries(TimeSeriesQueryParam param);
}
