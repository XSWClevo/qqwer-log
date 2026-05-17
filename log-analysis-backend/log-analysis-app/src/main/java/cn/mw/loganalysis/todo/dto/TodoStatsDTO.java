package cn.mw.loganalysis.todo.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 待办统计
 */
@Data
@Builder
public class TodoStatsDTO {

    private long total;

    private long todoCount;

    private long inProgressCount;

    private long doneCount;

    private long overdueCount;
}
