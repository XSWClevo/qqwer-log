package cn.mw.loganalysis.todo.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import cn.mw.loganalysis.common.serializer.FlexibleLocalDateTimeDeserializer;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建待办请求
 */
@Data
public class CreateTodoItemRequest {

    @NotBlank(message = "标题不能为空")
    private String title;

    private String description;

    private String priority = "MEDIUM";

    @JsonDeserialize(using = FlexibleLocalDateTimeDeserializer.class)
    private LocalDateTime dueAt;

    private List<String> tags;
}
