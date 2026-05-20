package cn.mw.loganalysis.vector.dto;

import lombok.Data;

@Data
public class PreviewVisualConfigRequest {

    /**
     * 流程图数据(JSON字符串)
     */
    private String graphData;
}
