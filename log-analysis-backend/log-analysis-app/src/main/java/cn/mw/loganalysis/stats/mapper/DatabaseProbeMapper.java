package cn.mw.loganalysis.stats.mapper;

import org.apache.ibatis.annotations.Mapper;

/**
 * 数据库连通性探测 Mapper
 */
@Mapper
public interface DatabaseProbeMapper {

    String selectVersion();

    String selectOne();
}
