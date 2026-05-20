package cn.mw.loganalysis.vector.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PreviewVisualConfigResponse {

    /**
     * 后端生成的 YAML 内容
     */
    private String content;
}
