package cn.mw.loganalysis.attack.dto;

import lombok.Data;

@Data
public class AttackDatasetQueryRequest {

    private String keyword;

    private String datasourceType;

    private Boolean enabled;

    private Integer pageNum = 1;

    private Integer pageSize = 20;
}
