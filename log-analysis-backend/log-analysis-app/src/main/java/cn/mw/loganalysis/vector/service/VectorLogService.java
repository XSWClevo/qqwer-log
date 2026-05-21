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
     * 分页查询日志（默认返回最新数据）
     */
    public Map<String, Object> queryLogs(String machineId, String fileName, String keyword,
                                         int pageNum, int pageSize) {
        Page<VectorLog> page = new Page<>(pageNum, pageSize);
        page = vectorLogMapper.selectLogsPage(page, machineId, fileName, keyword);

        Map<String, Object> result = new HashMap<>();
        result.put("logs", page.getRecords());
        result.put("total", page.getTotal());
        result.put("pageNum", page.getCurrent());
        result.put("pageSize", page.getSize());
        return result;
    }

    /**
     * 获取指定时间之后的日志（SSE 实时推送用）
     */
    public List<VectorLog> getLogsAfter(LocalDateTime afterTimestamp, String machineId, String fileName) {
        return vectorLogMapper.selectLogsAfter(afterTimestamp, machineId, fileName);
    }

    /**
     * 获取所有日志文件名（去重）
     */
    public List<String> getDistinctFileNames() {
        return vectorLogMapper.selectDistinctFileNames();
    }

    /**
     * 获取所有机器ID（去重）
     */
    public List<String> getDistinctMachineIds() {
        return vectorLogMapper.selectDistinctMachineIds();
    }
}
