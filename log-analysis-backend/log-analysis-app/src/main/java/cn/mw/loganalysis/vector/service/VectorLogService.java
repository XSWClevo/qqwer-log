package cn.mw.loganalysis.vector.service;

import cn.mw.loganalysis.vector.entity.VectorLog;
import cn.mw.loganalysis.vector.mapper.VectorLogMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vector 日志服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorLogService {

    private final VectorLogMapper vectorLogMapper;

    /**
     * 查询日志列表（分页）
     */
    public Map<String, Object> queryLogs(String machineId, String logLevel, String keyword,
                                         LocalDateTime startTime, LocalDateTime endTime,
                                         int pageNum, int pageSize) {
        Page<VectorLog> page = new Page<>(pageNum, pageSize);

        // 调用 Mapper 的 default 方法进行分页查询
        page = vectorLogMapper.selectLogsPage(page, machineId, logLevel, keyword, startTime, endTime);

        Map<String, Object> result = new HashMap<>();
        result.put("logs", page.getRecords());
        result.put("total", page.getTotal());
        result.put("pageNum", page.getCurrent());
        result.put("pageSize", page.getSize());
        result.put("totalPages", page.getPages());

        return result;
    }

    /**
     * 获取最新的日志（用于实时推送）
     */
    public List<VectorLog> getLogsAfter(LocalDateTime afterTimestamp, String machineId, String logLevel) {
        return vectorLogMapper.selectLogsAfter(afterTimestamp, machineId, logLevel);
    }

    /**
     * 获取所有主机名列表
     */
    public List<String> getDistinctHostnames() {
        return vectorLogMapper.selectDistinctHostnames();
    }

    /**
     * 获取所有IP地址列表
     */
    public List<String> getDistinctIpAddresses() {
        return vectorLogMapper.selectDistinctIpAddresses();
    }
}
