package cn.mw.loganalysis.vector.mapper;

import cn.mw.loganalysis.common.util.DateTimeUtils;
import cn.mw.loganalysis.vector.entity.VectorLog;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Vector 日志 Mapper（ClickHouse 数据源）
 */
@Mapper
@DS("clickhouse")
public interface VectorLogMapper extends BaseMapper<VectorLog> {

    /**
     * 分页查询日志（按时间倒序，返回最新数据）
     */
    default Page<VectorLog> selectLogsPage(Page<VectorLog> page, String machineId, String fileName,
                                           String keyword) {
        LambdaQueryWrapper<VectorLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(machineId), VectorLog::getMachineId, machineId);
        wrapper.eq(StringUtils.hasText(fileName), VectorLog::getFileName, fileName);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(VectorLog::getMessage, keyword);
        }
        wrapper.orderByDesc(VectorLog::getTimestamp);
        return selectPage(page, wrapper);
    }

    /**
     * 查询指定时间之后的日志（用于 SSE 实时推送）
     */
    default List<VectorLog> selectLogsAfter(LocalDateTime afterTimestamp, String machineId, String fileName) {
        LambdaQueryWrapper<VectorLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.gt(VectorLog::getTimestamp, DateTimeUtils.format(afterTimestamp));
        wrapper.eq(StringUtils.hasText(machineId), VectorLog::getMachineId, machineId);
        wrapper.eq(StringUtils.hasText(fileName), VectorLog::getFileName, fileName);
        wrapper.orderByAsc(VectorLog::getTimestamp)
               .last("LIMIT 500");
        return selectList(wrapper);
    }

    /**
     * 获取所有日志文件名（去重）
     */
    List<String> selectDistinctFileNames();

    /**
     * 获取所有机器ID（去重）
     */
    List<String> selectDistinctMachineIds();
}
