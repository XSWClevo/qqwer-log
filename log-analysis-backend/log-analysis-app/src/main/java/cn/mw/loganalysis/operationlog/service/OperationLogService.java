package cn.mw.loganalysis.operationlog.service;

import cn.mw.loganalysis.operationlog.dto.request.QueryOperationLogRequest;
import cn.mw.loganalysis.operationlog.dto.response.OperationLogDTO;
import cn.mw.loganalysis.operationlog.dto.response.OperationStatsDTO;
import cn.mw.loganalysis.operationlog.entity.UserOperationLog;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;
import java.util.Map;

/**
 * 操作日志 Service 接口
 *
 * @author Claude
 * @since 2026-01-07
 */
public interface OperationLogService {

    /**
     * 保存操作日志
     *
     * @param operationLog 操作日志
     */
    void saveLog(UserOperationLog operationLog);

    /**
     * 批量保存操作日志
     *
     * @param operationLogs 操作日志列表
     */
    void batchSaveLog(List<UserOperationLog> operationLogs);

    /**
     * 分页查询操作日志
     *
     * @param request 查询条件
     * @return 分页结果
     */
    Page<OperationLogDTO> queryLogs(QueryOperationLogRequest request);

    /**
     * 获取操作日志详情
     *
     * @param id 日志ID
     * @return 日志详情
     */
    OperationLogDTO getLogById(Long id);

    /**
     * 获取某用户最近的操作日志
     *
     * @param userId 用户ID
     * @param limit 数量限制
     * @return 操作日志列表
     */
    List<OperationLogDTO> getRecentLogsByUserId(Long userId, int limit);

    /**
     * 统计按操作类型分组
     *
     * @param startTime 开始时间 (格式: yyyy-MM-dd HH:mm:ss)
     * @param endTime 结束时间 (格式: yyyy-MM-dd HH:mm:ss)
     * @return 统计结果
     */
    List<OperationStatsDTO> statsByOperationType(String startTime, String endTime);

    /**
     * 统计按模块分组
     *
     * @param startTime 开始时间 (格式: yyyy-MM-dd HH:mm:ss)
     * @param endTime 结束时间 (格式: yyyy-MM-dd HH:mm:ss)
     * @return 统计结果
     */
    List<OperationStatsDTO> statsByModule(String startTime, String endTime);

    /**
     * 统计按用户分组
     *
     * @param startTime 开始时间 (格式: yyyy-MM-dd HH:mm:ss)
     * @param endTime 结束时间 (格式: yyyy-MM-dd HH:mm:ss)
     * @param limit TOP N 用户
     * @return 统计结果
     */
    List<OperationStatsDTO> statsByUser(String startTime, String endTime, int limit);

    /**
     * 检测异常操作 (用于实时告警)
     *
     * @param userId 用户ID
     * @param ipAddress IP 地址
     * @return 告警信息 (key: alertType, value: message)
     */
    Map<String, String> detectAnomalousOperations(Long userId, String ipAddress);

    /**
     * 归档旧数据
     *
     * @return 归档的记录数
     */
    int archiveOldLogs();
}
