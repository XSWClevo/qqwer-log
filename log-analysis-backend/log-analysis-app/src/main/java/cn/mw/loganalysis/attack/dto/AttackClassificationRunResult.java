package cn.mw.loganalysis.attack.dto;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class AttackClassificationRunResult {

    private int datasetCount;

    private int scannedCount;

    private int matchedCount;

    private int insertedCount;

    @Builder.Default
    private List<String> skippedDatasets = new ArrayList<>();
}
