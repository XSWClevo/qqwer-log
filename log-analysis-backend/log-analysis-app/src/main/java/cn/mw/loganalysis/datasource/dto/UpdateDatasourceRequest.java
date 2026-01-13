package cn.mw.loganalysis.datasource.dto;

import lombok.Data;

/**
 * 更新数据源请求
 */
@Data
public class UpdateDatasourceRequest {

    private String name;

    private String host;

    private Integer port;

    private String databaseName;

    private String username;

    private String password;

    private Boolean sslEnabled;

    private String connectionParams;

    private String description;

    private String status;
}
