package cn.mw.loganalysis.attack.dto;

import lombok.Data;

import java.util.List;

@Data
public class AttackClassificationRunRequest {

    private List<Long> datasetIds;

    private String startTime;

    private String endTime;

    private Integer limit;
}
