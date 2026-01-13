package cn.mw.loganalysis.dashboard.entity;

import lombok.Data;

/**
 * Top 实体查询结果行 (主机/应用)
 */
@Data
public class TopEntityRow {
    private String name;
    private Long cnt;
}
