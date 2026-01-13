package cn.mw.loganalysis.vector.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 生成 YAML 配置请求
 */
@Data
public class GenerateYamlRequest {
    
    /**
     * 组件类型: source, transform, sink
     */
    @NotBlank(message = "组件类型不能为空")
    private String componentType;
    
    /**
     * Vector 类型: file, kafka, remap, clickhouse 等
     */
    @NotBlank(message = "Vector类型不能为空")
    private String vectorType;
    
    /**
     * 可视化配置 JSON 字符串
     */
    @NotBlank(message = "配置内容不能为空")
    private String visualConfig;
}
