package cn.mw.loganalysis.common.config;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.ds.ItemDataSource;
import liquibase.integration.spring.SpringLiquibase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
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

    @Bean("clickhouseLiquibase")
    public SpringLiquibase clickhouseLiquibase() {
        DataSource clickhouseDataSource = DataSourceBuilder.create()
                .driverClassName(clickhouseDriverClassName)
                .url(clickhouseUrl)
                .username(clickhouseUsername)
                .password(clickhousePassword)
                .build();

        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(clickhouseDataSource);
        liquibase.setChangeLog("classpath:db/changelog/clickhouse/db.changelog-master.yaml");
        log.info("ClickHouse Liquibase 初始化完成");
        return liquibase;
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
