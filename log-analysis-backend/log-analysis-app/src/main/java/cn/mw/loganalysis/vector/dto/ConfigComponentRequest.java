package cn.mw.loganalysis.vector.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConfigComponentRequest {

    @NotBlank(message = "组件名称不能为空")
    private String name;

    @NotBlank(message = "组件类型不能为空")
    private String componentType;

    @NotBlank(message = "Vector类型不能为空")
    private String vectorType;

    @NotBlank(message = "配置内容不能为空")
    private String configYaml;

    /**
     * 可视化配置数据（JSON格式）
     */
    private String visualData;

    private String description;

    private Boolean isTemplate;

    /**
     * 是否可作为查询数据源（仅 Sink 组件有效）
     */
    private Boolean queryable;

    /**
     * 数据源显示名称
     */
    private String displayName;
}
