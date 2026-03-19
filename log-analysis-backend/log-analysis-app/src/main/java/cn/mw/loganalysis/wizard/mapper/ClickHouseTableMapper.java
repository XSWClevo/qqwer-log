package cn.mw.loganalysis.wizard.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * ClickHouse 表元数据 Mapper
 */
@Mapper
public interface ClickHouseTableMapper {

    List<String> selectTables(@Param("databaseExpression") String databaseExpression);

    List<Map<String, Object>> selectTableColumns(@Param("databaseExpression") String databaseExpression,
                                                 @Param("tableExpression") String tableExpression);
}
