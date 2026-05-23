package cn.mw.loganalysis.attack.mapper.param;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AttackDatasetScanSqlParam {

    private String tableExpression;

    private String timeExpression;

    private String messageExpression;

    private String rawExpression;

    private String sourceIpExpression;

    private String hostnameExpression;

    private String severityExpression;

    private String startTime;

    private String endTime;

    private int limit;
}
