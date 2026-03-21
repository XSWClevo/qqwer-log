package cn.mw.loganalysis.datasource.service;

import cn.mw.loganalysis.datasource.dto.CreateDatasourceRequest;
import cn.mw.loganalysis.datasource.dto.DatasourceTestResult;
import cn.mw.loganalysis.datasource.dto.UpdateDatasourceRequest;
import cn.mw.loganalysis.datasource.entity.Datasource;
import cn.mw.loganalysis.datasource.repository.DatasourceRepository;
import cn.mw.loganalysis.stats.mapper.DatabaseProbeMapper;
import cn.mw.loganalysis.stats.service.query.support.DynamicMyBatisUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.apache.ibatis.session.SqlSessionFactory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据源服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DatasourceService {

    private final DatasourceRepository datasourceRepository;

    /**
     * 创建数据源
     */
    @Transactional(rollbackFor = Exception.class)
    public Datasource createDatasource(CreateDatasourceRequest request) {
        // 检查名称是否已存在
        Datasource existing = datasourceRepository.findByName(request.getName());
        if (existing != null) {
            throw new IllegalArgumentException("数据源名称已存在: " + request.getName());
        }

        Datasource datasource = new Datasource();
        datasource.setName(request.getName());
        datasource.setType(request.getType());
        datasource.setHost(request.getHost());
        datasource.setPort(request.getPort());
        datasource.setDatabaseName(request.getDatabaseName());
        datasource.setUsername(request.getUsername());
        datasource.setPassword(request.getPassword()); // TODO: 加密存储
        datasource.setSslEnabled(request.getSslEnabled());
        datasource.setConnectionParams(request.getConnectionParams());
        datasource.setDescription(request.getDescription());
        datasource.setStatus("active");
        datasource.setCreatedAt(LocalDateTime.now());
        datasource.setUpdatedAt(LocalDateTime.now());

        datasourceRepository.save(datasource);
        log.info("创建数据源成功: id={}, name={}", datasource.getId(), datasource.getName());

        return datasource;
    }

    /**
     * 更新数据源
     */
    @Transactional(rollbackFor = Exception.class)
    public Datasource updateDatasource(String id, UpdateDatasourceRequest request) {
        Datasource datasource = datasourceRepository.findById(id);
        if (datasource == null) {
            throw new IllegalArgumentException("数据源不存在: " + id);
        }

        // 检查名称是否与其他数据源冲突
        if (StringUtils.isNotBlank(request.getName()) && !request.getName().equals(datasource.getName())) {
            Datasource existing = datasourceRepository.findByName(request.getName());
            if (existing != null) {
                throw new IllegalArgumentException("数据源名称已存在: " + request.getName());
            }
            datasource.setName(request.getName());
        }

        if (StringUtils.isNotBlank(request.getHost())) {
            datasource.setHost(request.getHost());
        }
        if (request.getPort() != null) {
            datasource.setPort(request.getPort());
        }
        if (StringUtils.isNotBlank(request.getDatabaseName())) {
            datasource.setDatabaseName(request.getDatabaseName());
        }
        if (StringUtils.isNotBlank(request.getUsername())) {
            datasource.setUsername(request.getUsername());
        }
        if (StringUtils.isNotBlank(request.getPassword())) {
            datasource.setPassword(request.getPassword()); // TODO: 加密存储
        }
        if (request.getSslEnabled() != null) {
            datasource.setSslEnabled(request.getSslEnabled());
        }
        if (StringUtils.isNotBlank(request.getConnectionParams())) {
            datasource.setConnectionParams(request.getConnectionParams());
        }
        if (StringUtils.isNotBlank(request.getDescription())) {
            datasource.setDescription(request.getDescription());
        }
        if (StringUtils.isNotBlank(request.getStatus())) {
            datasource.setStatus(request.getStatus());
        }

        datasource.setUpdatedAt(LocalDateTime.now());
        datasourceRepository.updateById(datasource);

        log.info("更新数据源成功: id={}, name={}", datasource.getId(), datasource.getName());
        return datasource;
    }

    /**
     * 删除数据源
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteDatasource(String id) {
        Datasource datasource = datasourceRepository.findById(id);
        if (datasource == null) {
            throw new IllegalArgumentException("数据源不存在: " + id);
        }

        // TODO: 检查是否有组件正在使用此数据源

        datasourceRepository.deleteById(id);
        log.info("删除数据源成功: id={}, name={}", id, datasource.getName());
    }

    /**
     * 获取数据源详情
     */
    public Datasource getDatasource(String id) {
        return datasourceRepository.findById(id);
    }

    /**
     * 分页查询数据源
     */
    public Page<Datasource> listDatasources(int pageNum, int pageSize, String keyword, String type, String status) {
        return datasourceRepository.findPageByCondition(pageNum, pageSize, keyword, type, status);
    }

    /**
     * 查询所有活跃的数据源
     */
    public List<Datasource> listActiveDatasources() {
        return datasourceRepository.findActive();
    }

    /**
     * 根据类型查询数据源
     */
    public List<Datasource> listDatasourcesByType(String type) {
        return datasourceRepository.findActiveByType(type);
    }

    /**
     * 测试数据源连接
     */
    public DatasourceTestResult testConnection(String id) {
        Datasource datasource = datasourceRepository.findById(id);
        if (datasource == null) {
            return DatasourceTestResult.builder()
                    .success(false)
                    .message("数据源不存在")
                    .build();
        }

        return testConnection(datasource);
    }

    /**
     * 测试数据源连接（使用数据源对象）
     */
    public DatasourceTestResult testConnection(Datasource datasource) {
        long startTime = System.currentTimeMillis();

        try {
            String jdbcUrl = buildJdbcUrl(datasource);
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setUrl(jdbcUrl);
            dataSource.setUsername(datasource.getUsername());
            dataSource.setPassword(datasource.getPassword());

            SqlSessionFactory sqlSessionFactory = DynamicMyBatisUtils.buildSqlSessionFactory(dataSource, DatabaseProbeMapper.class);
            String version = DynamicMyBatisUtils.execute(sqlSessionFactory, DatabaseProbeMapper.class,
                    mapper -> supportsVersionProbe(datasource.getType()) ? mapper.selectVersion() : mapper.selectOne());

            long responseTime = System.currentTimeMillis() - startTime;

            // 更新数据源检查状态
            datasource.setLastCheckTime(LocalDateTime.now());
            datasource.setLastCheckStatus("success");
            datasource.setLastCheckMessage("连接成功");
            datasourceRepository.updateById(datasource);

            return DatasourceTestResult.builder()
                    .success(true)
                    .message("连接成功")
                    .responseTime(responseTime)
                    .version(version)
                    .build();

        } catch (Exception e) {
            log.error("测试数据源连接失败: id={}, error={}", datasource.getId(), e.getMessage(), e);

            long responseTime = System.currentTimeMillis() - startTime;

            // 更新数据源检查状态
            datasource.setLastCheckTime(LocalDateTime.now());
            datasource.setLastCheckStatus("failed");
            datasource.setLastCheckMessage(e.getMessage());
            datasourceRepository.updateById(datasource);

            return DatasourceTestResult.builder()
                    .success(false)
                    .message("连接失败: " + e.getMessage())
                    .responseTime(responseTime)
                    .build();
        }
    }

    /**
     * 构建 JDBC URL
     */
    private String buildJdbcUrl(Datasource datasource) {
        String protocol = datasource.getSslEnabled() != null && datasource.getSslEnabled() ? "s" : "";

        switch (datasource.getType().toLowerCase()) {
            case "clickhouse":
                return String.format("jdbc:clickhouse%s://%s:%d/%s",
                        protocol, datasource.getHost(), datasource.getPort(),
                        datasource.getDatabaseName() != null ? datasource.getDatabaseName() : "default");

            case "postgresql":
                return String.format("jdbc:postgresql://%s:%d/%s",
                        datasource.getHost(), datasource.getPort(),
                        datasource.getDatabaseName() != null ? datasource.getDatabaseName() : "postgres");

            case "mysql":
                return String.format("jdbc:mysql://%s:%d/%s",
                        datasource.getHost(), datasource.getPort(),
                        datasource.getDatabaseName() != null ? datasource.getDatabaseName() : "mysql");

            default:
                throw new IllegalArgumentException("不支持的数据源类型: " + datasource.getType());
        }
    }

    private boolean supportsVersionProbe(String type) {
        if (StringUtils.isBlank(type)) {
            return false;
        }

        return switch (type.toLowerCase()) {
            case "clickhouse", "postgresql", "mysql" -> true;
            default -> false;
        };
    }
}
