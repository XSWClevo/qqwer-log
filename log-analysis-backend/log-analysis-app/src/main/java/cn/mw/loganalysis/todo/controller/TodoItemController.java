package cn.mw.loganalysis.todo.controller;

import cn.mw.loganalysis.common.exception.UnauthorizedException;
import cn.mw.loganalysis.common.response.Result;
import cn.mw.loganalysis.todo.dto.CreateTodoItemRequest;
import cn.mw.loganalysis.todo.dto.TodoItemQueryRequest;
import cn.mw.loganalysis.todo.dto.TodoStatsDTO;
import cn.mw.loganalysis.todo.dto.UpdateTodoItemRequest;
import cn.mw.loganalysis.todo.dto.UpdateTodoStatusRequest;
import cn.mw.loganalysis.todo.entity.TodoItem;
import cn.mw.loganalysis.todo.service.TodoItemService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * 待办事项控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
public class TodoItemController {

    private final TodoItemService todoItemService;

    @PostMapping
    public Result<TodoItem> create(@Valid @RequestBody CreateTodoItemRequest request, Authentication authentication) {
        Long userId = requireUserId(authentication);
        return Result.success(todoItemService.create(userId, request));
    }

    @PutMapping("/{id}")
    public Result<TodoItem> update(@PathVariable Long id,
                                   @Valid @RequestBody UpdateTodoItemRequest request,
                                   Authentication authentication) {
        Long userId = requireUserId(authentication);
        return Result.success(todoItemService.update(userId, id, request));
    }

    @PatchMapping("/{id}/status")
    public Result<TodoItem> updateStatus(@PathVariable Long id,
                                         @Valid @RequestBody UpdateTodoStatusRequest request,
                                         Authentication authentication) {
        Long userId = requireUserId(authentication);
        return Result.success(todoItemService.updateStatus(userId, id, request.getStatus()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, Authentication authentication) {
        Long userId = requireUserId(authentication);
        todoItemService.delete(userId, id);
        return Result.success();
    }

    @GetMapping("/{id}")
    public Result<TodoItem> getById(@PathVariable Long id, Authentication authentication) {
        Long userId = requireUserId(authentication);
        return Result.success(todoItemService.getById(userId, id));
    }

    @GetMapping
    public Result<Page<TodoItem>> list(TodoItemQueryRequest request, Authentication authentication) {
        Long userId = requireUserId(authentication);
        return Result.success(todoItemService.list(userId, request));
    }

    @GetMapping("/stats")
    public Result<TodoStatsDTO> stats(Authentication authentication) {
        Long userId = requireUserId(authentication);
        return Result.success(todoItemService.stats(userId));
    }

    private Long requireUserId(Authentication authentication) {
        if (ObjectUtils.isNotEmpty(authentication) && authentication.getPrincipal() instanceof Long userId) {
            return userId;
        }
        throw new UnauthorizedException("未获取到当前登录用户信息");
    }
}
