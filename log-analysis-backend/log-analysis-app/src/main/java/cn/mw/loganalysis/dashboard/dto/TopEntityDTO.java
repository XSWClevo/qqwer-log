package cn.mw.loganalysis.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Top 实体 DTO (主机/应用)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopEntityDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 实体类型 (host / app)
     */
    private String type;

    /**
     * Top 实体列表
     */
    private List<EntityCount> items;

    /**
     * 总实体数
     */
    private Long totalEntities;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EntityCount implements Serializable {
        private static final long serialVersionUID = 1L;
        
        /**
         * 实体名称 (主机名或应用名)
         */
        private String name;
        
        /**
         * 日志数量
         */
        private Long count;
        
        /**
         * 占比 (0-100)
         */
        private Double percentage;
    }
}
