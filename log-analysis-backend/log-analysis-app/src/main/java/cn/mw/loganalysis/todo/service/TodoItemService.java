package cn.mw.loganalysis.todo.service;

import cn.mw.loganalysis.common.exception.ResourceNotFoundException;
import cn.mw.loganalysis.common.exception.ValidationException;
import cn.mw.loganalysis.todo.dto.CreateTodoItemRequest;
import cn.mw.loganalysis.todo.dto.TodoItemQueryRequest;
import cn.mw.loganalysis.todo.dto.TodoStatsDTO;
import cn.mw.loganalysis.todo.dto.UpdateTodoItemRequest;
import cn.mw.loganalysis.todo.entity.TodoItem;
import cn.mw.loganalysis.todo.mapper.TodoItemMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * 待办事项服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TodoItemService {

    private static final List<String> VALID_STATUS = List.of("TODO", "IN_PROGRESS", "DONE");
    private static final List<String> VALID_PRIORITY = List.of("LOW", "MEDIUM", "HIGH", "URGENT");

    private final TodoItemMapper todoItemMapper;

    @Transactional
    public TodoItem create(Long userId, CreateTodoItemRequest request) {
        validatePriority(request.getPriority());

        TodoItem todoItem = new TodoItem();
        todoItem.setTitle(request.getTitle().trim());
        todoItem.setDescription(request.getDescription());
        todoItem.setStatus("TODO");
        todoItem.setPriority(normalizePriority(request.getPriority()));
        todoItem.setDueAt(request.getDueAt());
        todoItem.setTags(request.getTags());
        todoItem.setCreatedBy(userId);
        todoItem.setUpdatedBy(userId);
        todoItemMapper.insert(todoItem);
        return todoItem;
    }

    @Transactional
    public TodoItem update(Long userId, Long id, UpdateTodoItemRequest request) {
        TodoItem todoItem = getOwnedTodo(userId, id);

        if (StringUtils.isNotBlank(request.getTitle())) {
            todoItem.setTitle(request.getTitle().trim());
        }
        if (request.getDescription() != null) {
            todoItem.setDescription(request.getDescription());
        }
        if (StringUtils.isNotBlank(request.getStatus())) {
            todoItem.setStatus(normalizeStatus(request.getStatus()));
        }
        if (StringUtils.isNotBlank(request.getPriority())) {
            todoItem.setPriority(normalizePriority(request.getPriority()));
        }
        if (request.getDueAt() != null) {
            todoItem.setDueAt(request.getDueAt());
        }
        if (request.getTags() != null) {
            todoItem.setTags(request.getTags());
        }
        applyCompletedAt(todoItem);
        todoItem.setUpdatedBy(userId);
        todoItemMapper.updateById(todoItem);
        return todoItem;
    }

    @Transactional
    public TodoItem updateStatus(Long userId, Long id, String status) {
        TodoItem todoItem = getOwnedTodo(userId, id);
        todoItem.setStatus(normalizeStatus(status));
        applyCompletedAt(todoItem);
        todoItem.setUpdatedBy(userId);
        todoItemMapper.updateById(todoItem);
        return todoItem;
    }

    @Transactional
    public void delete(Long userId, Long id) {
        TodoItem todoItem = getOwnedTodo(userId, id);
        todoItemMapper.deleteById(todoItem.getId());
    }

    public TodoItem getById(Long userId, Long id) {
        return getOwnedTodo(userId, id);
    }

    public Page<TodoItem> list(Long userId, TodoItemQueryRequest request) {
        Page<TodoItem> page = new Page<>(ObjectUtils.defaultIfNull(request.getPageNum(), 1),
                ObjectUtils.defaultIfNull(request.getPageSize(), 20));
        LambdaQueryWrapper<TodoItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TodoItem::getCreatedBy, userId);

        if (StringUtils.isNotBlank(request.getKeyword())) {
            wrapper.and(query -> query.like(TodoItem::getTitle, request.getKeyword())
                    .or()
                    .like(TodoItem::getDescription, request.getKeyword()));
        }
        if (StringUtils.isNotBlank(request.getStatus())) {
            wrapper.eq(TodoItem::getStatus, normalizeStatus(request.getStatus()));
        }
        if (StringUtils.isNotBlank(request.getPriority())) {
            wrapper.eq(TodoItem::getPriority, normalizePriority(request.getPriority()));
        }
        if (Boolean.TRUE.equals(request.getOverdueOnly())) {
            wrapper.isNotNull(TodoItem::getDueAt)
                    .lt(TodoItem::getDueAt, LocalDateTime.now())
                    .ne(TodoItem::getStatus, "DONE");
        }

        wrapper.last("ORDER BY CASE status WHEN 'TODO' THEN 1 WHEN 'IN_PROGRESS' THEN 2 WHEN 'DONE' THEN 3 ELSE 4 END," +
                " CASE priority WHEN 'URGENT' THEN 1 WHEN 'HIGH' THEN 2 WHEN 'MEDIUM' THEN 3 WHEN 'LOW' THEN 4 ELSE 5 END," +
                " due_at ASC NULLS LAST, created_at DESC");
        return todoItemMapper.selectPage(page, wrapper);
    }

    public TodoStatsDTO stats(Long userId) {
        long total = countBy(userId, null, false);
        long todoCount = countBy(userId, "TODO", false);
        long inProgressCount = countBy(userId, "IN_PROGRESS", false);
        long doneCount = countBy(userId, "DONE", false);
        long overdueCount = countBy(userId, null, true);
        return TodoStatsDTO.builder()
                .total(total)
                .todoCount(todoCount)
                .inProgressCount(inProgressCount)
                .doneCount(doneCount)
                .overdueCount(overdueCount)
                .build();
    }

    private long countBy(Long userId, String status, boolean overdueOnly) {
        LambdaQueryWrapper<TodoItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TodoItem::getCreatedBy, userId);
        if (StringUtils.isNotBlank(status)) {
            wrapper.eq(TodoItem::getStatus, status);
        }
        if (overdueOnly) {
            wrapper.isNotNull(TodoItem::getDueAt)
                    .lt(TodoItem::getDueAt, LocalDateTime.now())
                    .ne(TodoItem::getStatus, "DONE");
        }
        return todoItemMapper.selectCount(wrapper);
    }

    private TodoItem getOwnedTodo(Long userId, Long id) {
        LambdaQueryWrapper<TodoItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TodoItem::getId, id)
                .eq(TodoItem::getCreatedBy, userId)
                .last("LIMIT 1");
        TodoItem todoItem = todoItemMapper.selectOne(wrapper);
        if (ObjectUtils.isEmpty(todoItem)) {
            throw new ResourceNotFoundException("待办事项不存在: " + id);
        }
        return todoItem;
    }

    private void applyCompletedAt(TodoItem todoItem) {
        if (StringUtils.equals(todoItem.getStatus(), "DONE")) {
            if (todoItem.getCompletedAt() == null) {
                todoItem.setCompletedAt(LocalDateTime.now());
            }
            return;
        }
        todoItem.setCompletedAt(null);
    }

    private String normalizeStatus(String status) {
        String normalized = StringUtils.upperCase(StringUtils.trim(status), Locale.ROOT);
        if (!VALID_STATUS.contains(normalized)) {
            throw new ValidationException("不支持的待办状态: " + status);
        }
        return normalized;
    }

    private String normalizePriority(String priority) {
        String normalized = StringUtils.upperCase(StringUtils.defaultIfBlank(priority, "MEDIUM"), Locale.ROOT);
        if (!VALID_PRIORITY.contains(normalized)) {
            throw new ValidationException("不支持的优先级: " + priority);
        }
        return normalized;
    }

    private void validatePriority(String priority) {
        normalizePriority(priority);
    }
}
