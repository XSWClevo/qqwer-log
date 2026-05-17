package cn.mw.loganalysis.todo.dto;

import lombok.Data;

/**
 * 待办列表查询请求
 */
@Data
public class TodoItemQueryRequest {

    private String keyword;

    private String status;

    private String priority;

    private Boolean overdueOnly;

    private Integer pageNum = 1;

    private Integer pageSize = 20;
}
