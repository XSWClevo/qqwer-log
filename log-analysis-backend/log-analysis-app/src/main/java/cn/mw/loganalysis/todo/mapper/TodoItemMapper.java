package cn.mw.loganalysis.todo.mapper;

import cn.mw.loganalysis.todo.entity.TodoItem;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 待办事项 Mapper
 */
@Mapper
@DS("postgres")
public interface TodoItemMapper extends BaseMapper<TodoItem> {
}
