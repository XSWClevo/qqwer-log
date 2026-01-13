package cn.mw.loganalysis.auth.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码工具类 - 用于生成BCrypt密码哈希
 */
public class PasswordHashGenerator {

    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        // 生成 admin123 的哈希
        String password = "admin123";
        String hash = encoder.encode(password);

        System.out.println("密码: " + password);
        System.out.println("BCrypt哈希: " + hash);
        System.out.println();

        // 验证哈希是否正确
        boolean matches = encoder.matches(password, hash);
        System.out.println("验证结果: " + matches);

        // 生成SQL语句
        System.out.println("\n----- SQL语句 -----");
        System.out.println("-- 插入默认管理员用户 (密码: admin123, BCrypt加密)");
        System.out.println("INSERT INTO users (username, password_hash, email, full_name, role, enabled)");
        System.out.println("VALUES ('admin', '" + hash + "', 'admin@example.com', 'System Administrator', 'ADMIN', true);");
    }
}
