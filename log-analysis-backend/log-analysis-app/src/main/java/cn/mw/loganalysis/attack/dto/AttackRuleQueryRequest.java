package cn.mw.loganalysis.attack.dto;

import lombok.Data;

@Data
public class AttackRuleQueryRequest {

    private String keyword;

    private String attackType;

    private Boolean enabled;

    private Integer pageNum = 1;

    private Integer pageSize = 20;
}
