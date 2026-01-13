package cn.mw.loganalysis.stats.service.query;

import lombok.Builder;
import lombok.Data;

/**
 * 建表结果
 */
@Data
@Builder
public class CreateTableResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 消息
     */
    private String message;

    /**
     * 执行的 SQL
     */
    private String executedSQL;
}
