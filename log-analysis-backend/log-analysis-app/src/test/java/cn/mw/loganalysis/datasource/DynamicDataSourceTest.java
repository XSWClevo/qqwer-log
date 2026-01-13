//package cn.mw.loganalysis.datasource;
//
//import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
//import lombok.extern.slf4j.Slf4j;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.context.SpringBootTest;
//import org.springframework.jdbc.core.JdbcTemplate;
//import org.springframework.test.context.ActiveProfiles;
//
//import javax.sql.DataSource;
//import java.util.Map;
//
//import static org.junit.jupiter.api.Assertions.*;
//
///**
// * 动态数据源测试类
// * 验证PostgreSQL和ClickHouse数据源配置及切换功能
// */
//@Slf4j
//@SpringBootTest
//@ActiveProfiles("test")
//class DynamicDataSourceTest {
//
//    @Autowired
//    private DataSource dataSource;
//
//    @Autowired
//    private JdbcTemplate jdbcTemplate;
//
//    /**
//     * 测试数据源类型
//     */
//    @Test
//    void testDataSourceType() {
//        assertNotNull(dataSource);
//        assertTrue(dataSource instanceof DynamicRoutingDataSource,
//                "数据源应该是DynamicRoutingDataSource类型");
//
//        DynamicRoutingDataSource dynamicDataSource = (DynamicRoutingDataSource) dataSource;
//        Map<String, DataSource> dataSources = dynamicDataSource.getDataSources();
//
//        log.info("已配置的数据源: {}", dataSources.keySet());
//
//        // 验证配置了两个数据源
//        assertTrue(dataSources.containsKey("postgres"), "应该包含postgres数据源");
//        assertTrue(dataSources.containsKey("clickhouse"), "应该包含clickhouse数据源");
//    }
//
//    /**
//     * 测试默认数据源
//     */
//    @Test
//    void testDefaultDataSource() {
//        DynamicRoutingDataSource dynamicDataSource = (DynamicRoutingDataSource) dataSource;
//        String primary = dynamicDataSource.getPrimary();
//
//        log.info("默认数据源: {}", primary);
//        assertEquals("postgres", primary, "默认数据源应该是postgres");
//    }
//
//    /**
//     * 测试PostgreSQL连接
//     */
//    @Test
//    void testPostgresConnection() {
//        try {
//            // 执行一个简单的查询验证连接
//            String result = jdbcTemplate.queryForObject(
//                    "SELECT current_database()",
//                    String.class
//            );
//
//            log.info("PostgreSQL连接成功, 当前数据库: {}", result);
//            assertNotNull(result);
//        } catch (Exception e) {
//            log.error("PostgreSQL连接测试失败", e);
//            fail("PostgreSQL连接失败: " + e.getMessage());
//        }
//    }
//
//    /**
//     * 测试数据源配置属性
//     */
//    @Test
//    void testDataSourceProperties() {
//        DynamicRoutingDataSource dynamicDataSource = (DynamicRoutingDataSource) dataSource;
//
//        log.info("严格模式: {}", dynamicDataSource.getStrict());
//        log.info("主数据源: {}", dynamicDataSource.getPrimary());
//
//        assertFalse(dynamicDataSource.getStrict(), "应该关闭严格模式");
//    }
//}
