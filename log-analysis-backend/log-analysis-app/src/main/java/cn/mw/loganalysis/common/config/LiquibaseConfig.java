package cn.mw.loganalysis.common.config;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.ds.ItemDataSource;
import liquibase.integration.spring.SpringLiquibase;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Liquibase 多数据源配置
 * <p>
 * 已排除 LiquibaseAutoConfiguration，由本配置类统一管理所有数据源的 Liquibase。
 * <ul>
 *   <li>PostgreSQL (primary) — classpath:db/changelog/db.changelog-master.yaml</li>
 *   <li>ClickHouse — classpath:db/changelog/clickhouse/db.changelog-master.yaml</li>
 * </ul>
 */
@Slf4j
@Configuration
public class LiquibaseConfig {

    @Value("${spring.datasource.dynamic.datasource.clickhouse.url}")
    private String clickhouseUrl;

    @Value("${spring.datasource.dynamic.datasource.clickhouse.username}")
    private String clickhouseUsername;

    @Value("${spring.datasource.dynamic.datasource.clickhouse.password}")
    private String clickhousePassword;

    @Value("${spring.datasource.dynamic.datasource.clickhouse.driver-class-name}")
    private String clickhouseDriverClassName;

    @Primary
    @Bean("postgresLiquibase")
    @DependsOn("dataSource")
    public SpringLiquibase postgresLiquibase(DataSource dataSource) {
        DataSource postgresDataSource = resolveDataSource(dataSource, "postgres");

        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(postgresDataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        log.info("PostgreSQL Liquibase 初始化完成");
        return liquibase;
    }

    @Bean("clickhouseSchemaInitializer")
    public InitializingBean clickhouseSchemaInitializer() {
        return () -> {
            DataSource clickhouseDataSource = buildClickHouseDataSource();
            try {
                runClickHouseChangelog(clickhouseDataSource);
                log.info("ClickHouse Schema 初始化完成");
            } finally {
                if (clickhouseDataSource instanceof AutoCloseable closeable) {
                    closeable.close();
                }
            }
        };
    }

    private DataSource buildClickHouseDataSource() {
        DataSource clickhouseDataSource = DataSourceBuilder.create()
                .driverClassName(clickhouseDriverClassName)
                .url(clickhouseUrl)
                .username(clickhouseUsername)
                .password(clickhousePassword)
                .build();
        return clickhouseDataSource;
    }

    private void runClickHouseChangelog(DataSource dataSource) throws Exception {
        List<String> changelogFiles = loadClickHouseChangelogFiles();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            for (String changelogFile : changelogFiles) {
                for (String sql : loadSqlStatements(changelogFile)) {
                    log.debug("执行 ClickHouse 初始化 SQL: {}", sql);
                    statement.execute(sql);
                }
            }
        }
    }

    private List<String> loadClickHouseChangelogFiles() throws Exception {
        ClassPathResource master = new ClassPathResource("db/changelog/clickhouse/db.changelog-master.yaml");
        List<String> files = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(master.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = StringUtils.trim(line);
                if (StringUtils.startsWith(trimmed, "file:")) {
                    files.add(StringUtils.trim(StringUtils.substringAfter(trimmed, "file:")));
                }
            }
        }
        if (files.isEmpty()) {
            throw new IllegalStateException("ClickHouse changelog 未配置 include 文件");
        }
        return files;
    }

    private List<String> loadSqlStatements(String changelogFile) throws Exception {
        String resourcePath = StringUtils.removeStart(changelogFile, "classpath:");
        ClassPathResource resource = new ClassPathResource(resourcePath);
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = StringUtils.trim(line);
                if (StringUtils.isBlank(trimmed) || StringUtils.startsWith(trimmed, "--")) {
                    continue;
                }
                current.append(line).append('\n');
                if (StringUtils.endsWith(trimmed, ";")) {
                    statements.add(StringUtils.removeEnd(StringUtils.trim(current.toString()), ";"));
                    current.setLength(0);
                }
            }
        }
        String remaining = StringUtils.trim(current.toString());
        if (StringUtils.isNotBlank(remaining)) {
            statements.add(StringUtils.removeEnd(remaining, ";"));
        }
        return statements;
    }

    private DataSource resolveDataSource(DataSource dataSource, String dataSourceKey) {
        if (dataSource instanceof DynamicRoutingDataSource dynamicDataSource) {
            Map<String, DataSource> dataSources = dynamicDataSource.getDataSources();
            DataSource resolved = dataSources.get(dataSourceKey);
            if (resolved != null) {
                if (resolved instanceof ItemDataSource itemDataSource) {
                    return itemDataSource.getRealDataSource();
                }
                return resolved;
            }
        }
        throw new IllegalStateException(
                "未找到 " + dataSourceKey + " 数据源，请检查 spring.datasource.dynamic 配置");
    }
}
