package cn.mw.loganalysis.todo.dto;

import cn.mw.loganalysis.common.serializer.FlexibleLocalDateTimeDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 更新待办请求
 */
@Data
public class UpdateTodoItemRequest {

    private String title;

    private String description;

    private String status;

    private String priority;

    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime dueAt;

    private List<String> tags;
}
