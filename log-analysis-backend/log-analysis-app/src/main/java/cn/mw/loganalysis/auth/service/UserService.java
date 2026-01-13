package cn.mw.loganalysis.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.mw.loganalysis.auth.entity.User;
import cn.mw.loganalysis.auth.mapper.UserMapper;
import cn.mw.loganalysis.common.exception.ResourceNotFoundException;
import cn.mw.loganalysis.common.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 创建用户
     */
    @Transactional
    public User createUser(User user) {
        // 检查用户名是否已存在
        User existingUser = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, user.getUsername())
        );
        
        if (existingUser != null) {
            throw new ValidationException("用户名已存在");
        }

        // 加密密码
        if (user.getPasswordHash() != null) {
            user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        }

        // 设置默认值
        if (user.getEnabled() == null) {
            user.setEnabled(true);
        }
        if (user.getRole() == null) {
            user.setRole("VIEWER");
        }

        userMapper.insert(user);
        log.info("User created: {}", user.getUsername());
        
        return user;
    }

    /**
     * 更新用户
     */
    @Transactional
    public User updateUser(Long id, User user) {
        User existingUser = userMapper.selectById(id);
        if (existingUser == null) {
            throw new ResourceNotFoundException("用户不存在");
        }

        // 更新字段
        if (user.getEmail() != null) {
            existingUser.setEmail(user.getEmail());
        }
        if (user.getFullName() != null) {
            existingUser.setFullName(user.getFullName());
        }
        if (user.getRole() != null) {
            existingUser.setRole(user.getRole());
        }
        if (user.getEnabled() != null) {
            existingUser.setEnabled(user.getEnabled());
        }

        userMapper.updateById(existingUser);
        log.info("User updated: {}", existingUser.getUsername());
        
        return existingUser;
    }

    /**
     * 删除用户
     */
    @Transactional
    public void deleteUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new ResourceNotFoundException("用户不存在");
        }

        userMapper.deleteById(id);
        log.info("User deleted: {}", user.getUsername());
    }

    /**
     * 获取用户
     */
    public User getUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new ResourceNotFoundException("用户不存在");
        }
        return user;
    }

    /**
     * 分页查询用户
     */
    public Page<User> listUsers(int pageNum, int pageSize, String role) {
        Page<User> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        
        if (role != null && !role.isEmpty()) {
            queryWrapper.eq(User::getRole, role);
        }
        
        return userMapper.selectPage(page, queryWrapper);
    }

    /**
     * 修改密码
     */
    @Transactional
    public void changePassword(Long id, String oldPassword, String newPassword) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new ResourceNotFoundException("用户不存在");
        }

        // 验证旧密码
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new ValidationException("旧密码错误");
        }

        // 更新密码
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        
        log.info("Password changed for user: {}", user.getUsername());
    }

    /**
     * 检查用户权限
     */
    public boolean hasPermission(Long userId, String permission) {
        User user = userMapper.selectById(userId);
        if (user == null || !user.getEnabled()) {
            return false;
        }

        // 简单的基于角色的权限检查
        String role = user.getRole();
        
        return switch (role) {
            case "ADMIN" -> true;  // 管理员拥有所有权限
            case "ANALYST" -> !permission.equals("ADMIN");  // 分析师除了管理权限外都有
            case "VIEWER" -> permission.equals("READ");  // 查看者只有读权限
            default -> false;
        };
    }
}
