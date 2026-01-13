package cn.mw.loganalysis.vector.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 添加机器请求DTO
 */
@Data
public class AddMachineRequest {

    /**
     * 机器名称
     */
    @NotBlank(message = "机器名称不能为空")
    private String name;

    /**
     * 主机名
     */
    @NotBlank(message = "主机名不能为空")
    private String hostname;

    /**
     * IP地址
     */
    @NotBlank(message = "IP地址不能为空")
    private String ipAddress;

    /**
     * SSH端口
     */
    @NotNull(message = "SSH端口不能为空")
    private Integer sshPort;

    /**
     * SSH用户
     */
    @NotBlank(message = "SSH用户不能为空")
    private String sshUser;

    /**
     * SSH密钥路径
     */
    private String sshKeyPath;

    /**
     * 操作系统类型
     */
    private String osType;

    /**
     * Vector安装路径
     */
    private String vectorInstallPath;

    /**
     * Vector配置文件路径
     */
    private String vectorConfigPath;

    /**
     * 管理方式: systemctl/binary
     */
    private String managementMethod;
}
