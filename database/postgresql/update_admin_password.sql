-- 更新管理员用户的密码哈希
-- 密码: admin123
-- 执行此脚本来修复密码验证问题

UPDATE users
SET password_hash = '$2a$10$9Sp6shUuNi3tQ2u5TkBmPu9/EI/RBDJONSjPuKvFtcYjbP5wkoV5O'
WHERE username = 'admin';

-- 验证更新
SELECT username,
       SUBSTRING(password_hash, 1, 20) || '...' as password_hash_preview,
       email,
       role,
       enabled
FROM users
WHERE username = 'admin';
