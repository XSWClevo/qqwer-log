package cn.mw.loganalysis.auth.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.mw.loganalysis.auth.dto.ChangePasswordRequest;
import cn.mw.loganalysis.auth.dto.CreateUserRequest;
import cn.mw.loganalysis.auth.dto.UpdateUserRequest;
import cn.mw.loganalysis.auth.entity.User;
import cn.mw.loganalysis.auth.service.UserService;
import cn.mw.loganalysis.common.response.Result;
import cn.mw.loganalysis.operationlog.annotation.OperationLog;
import cn.mw.loganalysis.operationlog.enums.OperationAction;
import cn.mw.loganalysis.operationlog.enums.OperationModule;
import cn.mw.loganalysis.operationlog.enums.OperationType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 创建用户
     */
    @PostMapping
    @OperationLog(
        module = OperationModule.USER,
        operationType = OperationType.CREATE,
        action = OperationAction.CREATE_USER,
        resourceType = "User",
        resourceIdSpEL = "#result.data.id",
        sensitiveFields = {"password"}
    )
    public Result<User> createUser(@Valid @RequestBody CreateUserRequest request) {
        log.info("Creating user: {}", request.getUsername());

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPasswordHash(request.getPassword());  // UserService会加密
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setRole(request.getRole());

        User createdUser = userService.createUser(user);

        // 清除密码字段再返回
        createdUser.setPasswordHash(null);
        return Result.success(createdUser);
    }

    /**
     * 更新用户
     */
    @PutMapping("/{id}")
    @OperationLog(
        module = OperationModule.USER,
        operationType = OperationType.UPDATE,
        action = OperationAction.UPDATE_USER,
        resourceType = "User",
        resourceIdSpEL = "#id"
    )
    public Result<User> updateUser(@PathVariable Long id,
                                   @Valid @RequestBody UpdateUserRequest request) {
        log.info("Updating user: {}", id);

        User user = new User();
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setRole(request.getRole());
        user.setEnabled(request.getEnabled());

        User updatedUser = userService.updateUser(id, user);

        // 清除密码字段再返回
        updatedUser.setPasswordHash(null);
        return Result.success(updatedUser);
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    @OperationLog(
        module = OperationModule.USER,
        operationType = OperationType.DELETE,
        action = OperationAction.DELETE_USER,
        resourceType = "User",
        resourceIdSpEL = "#id"
    )
    public Result<Void> deleteUser(@PathVariable Long id) {
        log.info("Deleting user: {}", id);
        userService.deleteUser(id);
        return Result.success();
    }

    /**
     * 获取用户详情
     */
    @GetMapping("/{id}")
    public Result<User> getUser(@PathVariable Long id) {
        log.info("Getting user: {}", id);
        User user = userService.getUser(id);

        // 清除密码字段再返回
        user.setPasswordHash(null);
        return Result.success(user);
    }

    /**
     * 分页查询用户列表
     */
    @GetMapping
    public Result<Page<User>> listUsers(@RequestParam(defaultValue = "1") int pageNum,
                                        @RequestParam(defaultValue = "10") int pageSize,
                                        @RequestParam(required = false) String role) {
        log.info("Listing users: page={}, size={}, role={}", pageNum, pageSize, role);
        Page<User> page = userService.listUsers(pageNum, pageSize, role);

        // 清除所有密码字段
        page.getRecords().forEach(user -> user.setPasswordHash(null));
        return Result.success(page);
    }

    /**
     * 修改密码
     */
    @PutMapping("/{id}/password")
    @OperationLog(
        module = OperationModule.USER,
        operationType = OperationType.UPDATE,
        action = OperationAction.CHANGE_PASSWORD,
        resourceType = "User",
        resourceIdSpEL = "#id",
        sensitiveFields = {"oldPassword", "newPassword"}
    )
    public Result<Void> changePassword(@PathVariable Long id,
                                      @Valid @RequestBody ChangePasswordRequest request) {
        log.info("Changing password for user: {}", id);
        userService.changePassword(id, request.getOldPassword(), request.getNewPassword());
        return Result.success();
    }

    /**
     * 检查用户权限
     */
    @GetMapping("/{id}/permissions/{permission}")
    public Result<Boolean> checkPermission(@PathVariable Long id,
                                           @PathVariable String permission) {
        log.info("Checking permission {} for user: {}", permission, id);
        boolean hasPermission = userService.hasPermission(id, permission);
        return Result.success(hasPermission);
    }
}
