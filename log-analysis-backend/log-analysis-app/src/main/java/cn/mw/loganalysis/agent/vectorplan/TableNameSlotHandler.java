package cn.mw.loganalysis.agent.vectorplan;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class TableNameSlotHandler implements CreateLogParserSlotHandler {

    private static final DateTimeFormatter TABLE_SUFFIX_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 在日志样本之后提取表名。
     */
    @Override
    public int getOrder() {
        return 20;
    }

    /**
     * 表名槽位处理器对每轮消息都可尝试提取。
     */
    @Override
    public boolean supports(CreateLogParserSlotContext context) {
        return true;
    }

    /**
     * 提取用户指定表名，或在用户明确要求时自动生成表名。
     */
    @Override
    public void fill(CreateLogParserSlotContext context) {
        String tableName = CreateLogParserSlotTextSupport.extractTableName(context.message());
        if (StringUtils.isNotBlank(tableName)) {
            context.frame().setTableName(tableName);
            if (StringUtils.isBlank(context.frame().getComponentPrefix())) {
                context.frame().setComponentPrefix(tableName);
            }
            return;
        }

        if (StringUtils.isBlank(context.frame().getTableName())
                && CreateLogParserSlotTextSupport.asksAutoTableName(context.message())) {
            String generatedTableName = "agent_logs_" + LocalDateTime.now().format(TABLE_SUFFIX_FORMATTER);
            context.frame().setTableName(generatedTableName);
            context.frame().setComponentPrefix(generatedTableName);
        }
    }
}
