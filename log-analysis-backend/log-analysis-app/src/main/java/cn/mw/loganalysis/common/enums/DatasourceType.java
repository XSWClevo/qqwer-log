package cn.mw.loganalysis.common.enums;

import cn.mw.loganalysis.datasource.entity.Datasource;
import lombok.Getter;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * 数据源类型枚举
 */
@Getter
public enum DatasourceType {
    CLICKHOUSE("clickhouse", "com.clickhouse.jdbc.ClickHouseDriver", "default", true) {
        @Override
        public String buildJdbcUrl(Datasource datasource) {
            return appendConnectionParams(
                    String.format("jdbc:clickhouse%s://%s:%d/%s",
                            BooleanUtils.isTrue(datasource.getSslEnabled()) ? "s" : "",
                            datasource.getHost(),
                            datasource.getPort(),
                            StringUtils.defaultIfBlank(datasource.getDatabaseName(), getDefaultDatabaseName())),
                    datasource.getConnectionParams()
            );
        }
    },
    POSTGRESQL("postgresql", "org.postgresql.Driver", "postgres", true) {
        @Override
        public String buildJdbcUrl(Datasource datasource) {
            return appendConnectionParams(
                    String.format("jdbc:postgresql://%s:%d/%s",
                            datasource.getHost(),
                            datasource.getPort(),
                            StringUtils.defaultIfBlank(datasource.getDatabaseName(), getDefaultDatabaseName())),
                    mergePostgreSqlParams(datasource.getConnectionParams(), datasource.getSslEnabled())
            );
        }
    },
    MYSQL("mysql", "com.mysql.cj.jdbc.Driver", "mysql", true) {
        @Override
        public String buildJdbcUrl(Datasource datasource) {
            return appendConnectionParams(
                    String.format("jdbc:mysql://%s:%d/%s",
                            datasource.getHost(),
                            datasource.getPort(),
                            StringUtils.defaultIfBlank(datasource.getDatabaseName(), getDefaultDatabaseName())),
                    mergeMySqlParams(datasource.getConnectionParams(), datasource.getSslEnabled())
            );
        }
    },
    ELASTICSEARCH("elasticsearch", StringUtils.EMPTY, StringUtils.EMPTY, false) {
        @Override
        public String buildJdbcUrl(Datasource datasource) {
            throw unsupportedJdbcOperation(datasource);
        }
    },
    LOKI("loki", StringUtils.EMPTY, StringUtils.EMPTY, false) {
        @Override
        public String buildJdbcUrl(Datasource datasource) {
            throw unsupportedJdbcOperation(datasource);
        }
    };

    private final String code;
    private final String driverClassName;
    private final String defaultDatabaseName;
    private final boolean versionProbeSupported;

    DatasourceType(String code, String driverClassName, String defaultDatabaseName, boolean versionProbeSupported) {
        this.code = code;
        this.driverClassName = driverClassName;
        this.defaultDatabaseName = defaultDatabaseName;
        this.versionProbeSupported = versionProbeSupported;
    }

    public abstract String buildJdbcUrl(Datasource datasource);

    public static DatasourceType fromCode(String code) {
        for (DatasourceType datasourceType : values()) {
            if (StringUtils.equalsIgnoreCase(datasourceType.code, code)) {
                return datasourceType;
            }
        }

        throw new IllegalArgumentException("不支持的数据源类型: " + code);
    }

    protected final String appendConnectionParams(String baseUrl, String connectionParams) {
        if (StringUtils.isBlank(connectionParams)) {
            return baseUrl;
        }

        String normalizedParams = StringUtils.removeStart(connectionParams.trim(), "?");
        if (StringUtils.isBlank(normalizedParams)) {
            return baseUrl;
        }

        return baseUrl + (StringUtils.contains(baseUrl, '?') ? "&" : "?") + normalizedParams;
    }

    protected final String mergePostgreSqlParams(String connectionParams, Boolean sslEnabled) {
        String normalizedParams = normalizeConnectionParams(connectionParams);

        if (!BooleanUtils.isTrue(sslEnabled)) {
            return normalizedParams;
        }

        StringBuilder builder = new StringBuilder(normalizedParams);
        appendParamIfMissing(builder, normalizedParams, "ssl", "true");
        appendParamIfMissing(builder, normalizedParams, "sslmode", "require");
        return builder.toString();
    }

    protected final String mergeMySqlParams(String connectionParams, Boolean sslEnabled) {
        String normalizedParams = normalizeConnectionParams(connectionParams);

        if (!BooleanUtils.isTrue(sslEnabled)) {
            return normalizedParams;
        }

        StringBuilder builder = new StringBuilder(normalizedParams);
        appendParamIfMissing(builder, normalizedParams, "useSSL", "true");
        appendParamIfMissing(builder, normalizedParams, "requireSSL", "true");
        return builder.toString();
    }

    private String normalizeConnectionParams(String connectionParams) {
        if (StringUtils.isBlank(connectionParams)) {
            return StringUtils.EMPTY;
        }

        return StringUtils.removeStart(connectionParams.trim(), "?");
    }

    private void appendParamIfMissing(StringBuilder builder, String currentParams, String key, String value) {
        if (StringUtils.containsIgnoreCase(currentParams, key + "=")) {
            return;
        }

        if (builder.length() > 0) {
            builder.append('&');
        }
        builder.append(key).append('=').append(value);
    }

    private static IllegalArgumentException unsupportedJdbcOperation(Datasource datasource) {
        return new IllegalArgumentException("数据源类型不支持 JDBC 测试: " + datasource.getType());
    }
}
