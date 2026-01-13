package cn.mw.loganalysis.vector.dto;

import lombok.Data;

/**
 * 更新可视化配置请求
 */
@Data
public class UpdateVisualConfigRequest {

    private String name;

    private String description;

    /**
     * 流程图数据 (JSON字符串)
     */
    private String graphData;

    /**
     * 生成的配置内容
     */
    private String content;

    /**
     * 节点数量
     */
    private Integer nodeCount;
}
