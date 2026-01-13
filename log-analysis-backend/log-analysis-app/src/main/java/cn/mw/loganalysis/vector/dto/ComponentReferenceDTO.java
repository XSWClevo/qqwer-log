package cn.mw.loganalysis.vector.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 组件引用信息 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComponentReferenceDTO {

    /**
     * 组件ID
     */
    private String componentId;

    /**
     * 组件名称
     */
    private String componentName;

    /**
     * 引用该组件的配置列表
     */
    private List<ConfigReference> references;

    /**
     * 引用数量
     */
    private int referenceCount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfigReference {
        /**
         * 配置ID
         */
        private String configId;

        /**
         * 配置名称
         */
        private String configName;

        /**
         * 节点在配置中的名称
         */
        private String nodeName;
    }
}
