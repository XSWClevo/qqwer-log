package cn.mw.loganalysis.stats.mapper;

import cn.mw.loganalysis.stats.mapper.param.ContextLogQueryParam;
import cn.mw.loganalysis.stats.mapper.param.DimensionStatsQueryParam;
import cn.mw.loganalysis.stats.mapper.param.FieldTimeSeriesQueryParam;
import cn.mw.loganalysis.stats.mapper.param.LogQuerySqlParam;
import cn.mw.loganalysis.stats.mapper.param.TimeSeriesQueryParam;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * ClickHouse 查询 Mapper
 */
@Mapper
public interface ClickHouseQueryMapper {

    String selectVersion();

    Long countTables(@Param("database") String database, @Param("tableName") String tableName);

    List<Map<String, Object>> selectTableSchemaRows(@Param("database") String database, @Param("tableName") String tableName);

    Long countTableRows(@Param("tableName") String tableName);

    List<String> selectTables(@Param("database") String database);

    List<Map<String, Object>> selectLogs(LogQuerySqlParam param);

    Long countLogs(LogQuerySqlParam param);

    List<Map<String, Object>> selectContextBeforeLogs(ContextLogQueryParam param);

    List<Map<String, Object>> selectContextAfterLogs(ContextLogQueryParam param);

    List<Map<String, Object>> selectDimensionStats(DimensionStatsQueryParam param);

    List<Map<String, Object>> selectTimeSeries(TimeSeriesQueryParam param);

    List<Map<String, Object>> selectFieldTimeSeries(FieldTimeSeriesQueryParam param);
}
