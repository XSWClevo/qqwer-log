package cn.mw.loganalysis.vector.dto;

import lombok.Data;
import java.util.Map;

/**
 * 组件状态响应
 */
@Data
public class ComponentStatusResponse {
    /**
     * 组件状态映射
     * key: 组件名称
     * value: 状态 (normal, warning, error, stopped)
     */
    private Map<String, String> componentStatus;
    
    /**
     * Vector 是否运行中
     */
    private Boolean vectorRunning;
    
    /**
     * 最后更新时间
     */
    private String lastUpdated;
}
