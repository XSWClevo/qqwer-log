package cn.mw.loganalysis.alert.service;

import cn.mw.loganalysis.alert.dto.AlertEventDTO;
import cn.mw.loganalysis.alert.dto.AlertEventQueryRequest;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.Map;

/**
 * 告警事件服务接口。
 */
public interface AlertEventService {

    IPage<AlertEventDTO> queryEvents(AlertEventQueryRequest request);

    AlertEventDTO getEventById(Long id);

    Map<String, Object> getAlertTrend(String timeRange);

    void acknowledgeEvent(Long id, Long userId);
}
