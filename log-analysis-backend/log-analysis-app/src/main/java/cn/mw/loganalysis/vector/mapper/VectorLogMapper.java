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
 * Vector 日志 Mapper
 * 使用 ClickHouse 数据源
 */
@Mapper
@DS("clickhouse")
public interface VectorLogMapper extends BaseMapper<VectorLog> {

    /**
     * 分页查询日志
     * 使用 default 方法构建查询条件
     */
    default Page<VectorLog> selectLogsPage(Page<VectorLog> page, String machineId, String logLevel,
                                           String keyword, LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<VectorLog> wrapper = new LambdaQueryWrapper<>();

        // 机器ID筛选
        wrapper.eq(StringUtils.hasText(machineId), VectorLog::getMachineId, machineId);

        // 日志级别筛选
        wrapper.eq(StringUtils.hasText(logLevel), VectorLog::getLogLevel, logLevel);

        // 关键词搜索（message 或 rawLog）
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(VectorLog::getMessage, keyword)
                             .or()
                             .like(VectorLog::getRawLog, keyword));
        }

        // 时间范围筛选
        if (startTime != null) {
            String startTimeStr = DateTimeUtils.format(startTime);
            wrapper.apply("timestamp >= toDateTime({0})", startTimeStr);
        }
        if (endTime != null) {
            String endTimeStr = DateTimeUtils.format(endTime);
            wrapper.apply("timestamp <= toDateTime({0})", endTimeStr);
        }

        // 按时间倒序
        wrapper.orderByDesc(VectorLog::getTimestamp);

        return selectPage(page, wrapper);
    }

    /**
     * 查询指定时间之后的日志（用于实时推送）
     */
    default List<VectorLog> selectLogsAfter(LocalDateTime afterTimestamp, String machineId, String logLevel) {
        LambdaQueryWrapper<VectorLog> wrapper = new LambdaQueryWrapper<>();

        // 时间筛选
        String afterTimeStr = DateTimeUtils.format(afterTimestamp);
        wrapper.apply("timestamp > toDateTime({0})", afterTimeStr);

        // 机器ID筛选
        wrapper.eq(StringUtils.hasText(machineId), VectorLog::getMachineId, machineId);

        // 日志级别筛选
        wrapper.eq(StringUtils.hasText(logLevel), VectorLog::getLogLevel, logLevel);

        // 按时间正序，限制100条
        wrapper.orderByAsc(VectorLog::getTimestamp)
               .last("LIMIT 100");

        return selectList(wrapper);
    }

    /**
     * 统计日志数量
     */
    default long countLogs(String machineId, String logLevel, String keyword,
                          LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<VectorLog> wrapper = new LambdaQueryWrapper<>();

        // 机器ID筛选
        wrapper.eq(StringUtils.hasText(machineId), VectorLog::getMachineId, machineId);

        // 日志级别筛选
        wrapper.eq(StringUtils.hasText(logLevel), VectorLog::getLogLevel, logLevel);

        // 关键词搜索
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(VectorLog::getMessage, keyword)
                             .or()
                             .like(VectorLog::getRawLog, keyword));
        }

        // 时间范围筛选
        if (startTime != null) {
            String startTimeStr = DateTimeUtils.format(startTime);
            wrapper.apply("timestamp >= toDateTime({0})", startTimeStr);
        }
        if (endTime != null) {
            String endTimeStr = DateTimeUtils.format(endTime);
            wrapper.apply("timestamp <= toDateTime({0})", endTimeStr);
        }

        return selectCount(wrapper);
    }

    /**
     * 获取所有主机列表（去重）
     */
    List<String> selectDistinctHostnames();

    /**
     * 获取所有IP地址列表（去重）
     */
    List<String> selectDistinctIpAddresses();
}
