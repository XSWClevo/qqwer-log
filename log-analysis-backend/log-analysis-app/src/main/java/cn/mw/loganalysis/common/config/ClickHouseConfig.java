//package cn.mw.loganalysis.common.config;
//
//import com.clickhouse.jdbc.ClickHouseDataSource;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.jdbc.core.JdbcTemplate;
//
//import javax.sql.DataSource;
//import java.sql.SQLException;
//import java.util.Properties;
//
///**
// * ClickHouse数据源配置
// */
//@Slf4j
//@Configuration
//public class ClickHouseConfig {
//
//    @Value("${clickhouse.url}")
//    private String url;
//
//    @Value("${clickhouse.username}")
//    private String username;
//
//    @Value("${clickhouse.password}")
//    private String password;
//
//    @Value("${clickhouse.socket-timeout:300000}")
//    private Integer socketTimeout;
//
//    @Value("${clickhouse.connection-timeout:10000}")
//    private Integer connectionTimeout;
//
//    /**
//     * 创建ClickHouse数据源
//     */
//    @Bean(name = "clickHouseDataSource")
//    public DataSource clickHouseDataSource() throws SQLException {
//        Properties properties = new Properties();
//        properties.setProperty("socket_timeout", String.valueOf(socketTimeout));
//        properties.setProperty("connect_timeout", String.valueOf(connectionTimeout));
//
//        ClickHouseDataSource dataSource = new ClickHouseDataSource(url, properties);
//
//        log.info("ClickHouse DataSource initialized: {}", url);
//        return dataSource;
//    }
//
//    /**
//     * 创建ClickHouse JdbcTemplate
//     */
//    @Bean(name = "clickHouseJdbcTemplate")
//    public JdbcTemplate clickHouseJdbcTemplate() throws SQLException {
//        return new JdbcTemplate(clickHouseDataSource());
//    }
//}
