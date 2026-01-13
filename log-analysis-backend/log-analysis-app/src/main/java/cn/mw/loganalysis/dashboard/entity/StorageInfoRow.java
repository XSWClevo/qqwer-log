package cn.mw.loganalysis.dashboard.entity;

import lombok.Data;

/**
 * 存储信息查询结果行
 */
@Data
public class StorageInfoRow {
    private Long usedBytes;
    private Integer partitionCount;
    private Long totalRows;
}
