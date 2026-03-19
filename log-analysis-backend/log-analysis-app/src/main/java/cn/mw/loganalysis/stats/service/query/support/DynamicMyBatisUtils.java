package cn.mw.loganalysis.stats.service.query.support;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.type.JdbcType;

import javax.sql.DataSource;
import java.io.InputStream;
import java.util.function.Function;

/**
 * 动态 MyBatis 工具类
 */
public final class DynamicMyBatisUtils {

    private DynamicMyBatisUtils() {
    }

    public static SqlSessionFactory buildSqlSessionFactory(DataSource dataSource, Class<?>... mapperTypes) {
        TransactionFactory transactionFactory = new JdbcTransactionFactory();
        Environment environment = new Environment("dynamic-mybatis", transactionFactory, dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setJdbcTypeForNull(JdbcType.OTHER);

        for (Class<?> mapperType : mapperTypes) {
            loadMapperXml(configuration, mapperType);
        }

        return new SqlSessionFactoryBuilder().build(configuration);
    }

    public static <T, R> R execute(SqlSessionFactory sqlSessionFactory, Class<T> mapperType, Function<T, R> callback) {
        try (SqlSession sqlSession = sqlSessionFactory.openSession()) {
            return callback.apply(sqlSession.getMapper(mapperType));
        }
    }

    private static void loadMapperXml(Configuration configuration, Class<?> mapperType) {
        String resource = "mapper/" + mapperType.getSimpleName() + ".xml";
        try (InputStream inputStream = Resources.getResourceAsStream(resource)) {
            XMLMapperBuilder mapperParser = new XMLMapperBuilder(
                    inputStream,
                    configuration,
                    resource,
                    configuration.getSqlFragments()
            );
            mapperParser.parse();
        } catch (Exception ex) {
            throw new IllegalStateException("加载 MyBatis Mapper 失败: " + resource, ex);
        }
    }
}
