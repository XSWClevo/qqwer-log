package cn.mw.loganalysis.datasource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建数据源请求
 */
@Data
public class CreateDatasourceRequest {

    @NotBlank(message = "数据源名称不能为空")
    private String name;

    @NotBlank(message = "数据源类型不能为空")
    private String type;

    @NotBlank(message = "主机地址不能为空")
    private String host;

    @NotNull(message = "端口号不能为空")
    private Integer port;

    private String databaseName;

    private String username;

    private String password;

    private Boolean sslEnabled = false;

    private String connectionParams;

    private String description;
}
