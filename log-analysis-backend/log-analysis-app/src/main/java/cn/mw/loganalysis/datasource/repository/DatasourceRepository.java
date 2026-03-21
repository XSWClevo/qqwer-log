package cn.mw.loganalysis.datasource.repository;

import cn.mw.loganalysis.datasource.entity.Datasource;
import cn.mw.loganalysis.datasource.mapper.DatasourceMapper;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

/**
 * 数据源仓储。
 *
 * 简单表查询、分页和更新在这里用 MyBatis-Plus wrapper 组装，
 * 避免继续把简单条件逻辑堆在 Mapper 默认方法里。
 */
@Repository
@DS("postgres")
@RequiredArgsConstructor
public class DatasourceRepository {

    private final DatasourceMapper datasourceMapper;

    public Datasource findById(String id) {
        return StringUtils.isBlank(id) ? null : datasourceMapper.selectById(StringUtils.trim(id));
    }

    public Datasource findByName(String name) {
        if (StringUtils.isBlank(name)) {
            return null;
        }

        return datasourceMapper.selectOne(
                Wrappers.<Datasource>lambdaQuery()
                        .eq(Datasource::getName, StringUtils.trim(name))
        );
    }

    public Page<Datasource> findPageByCondition(int pageNum, int pageSize, String keyword, String type, String status) {
        Page<Datasource> page = new Page<>(pageNum, pageSize);
        return datasourceMapper.selectPage(
                page,
                Wrappers.<Datasource>lambdaQuery()
                        .and(StringUtils.isNotBlank(keyword), wrapper -> wrapper
                                .like(Datasource::getName, StringUtils.trim(keyword))
                                .or()
                                .like(Datasource::getDescription, StringUtils.trim(keyword)))
                        .eq(StringUtils.isNotBlank(type), Datasource::getType, StringUtils.trim(type))
                        .eq(StringUtils.isNotBlank(status), Datasource::getStatus, StringUtils.trim(status))
                        .orderByDesc(Datasource::getCreatedAt)
        );
    }

    public List<Datasource> findActive() {
        return datasourceMapper.selectList(
                Wrappers.<Datasource>lambdaQuery()
                        .eq(Datasource::getStatus, "active")
                        .orderByDesc(Datasource::getCreatedAt)
        );
    }

    public List<Datasource> findActiveByType(String type) {
        if (StringUtils.isBlank(type)) {
            return Collections.emptyList();
        }

        return datasourceMapper.selectList(
                Wrappers.<Datasource>lambdaQuery()
                        .eq(Datasource::getType, StringUtils.trim(type))
                        .eq(Datasource::getStatus, "active")
                        .orderByDesc(Datasource::getCreatedAt)
        );
    }

    public void save(Datasource datasource) {
        if (datasource == null) {
            return;
        }
        datasourceMapper.insert(datasource);
    }

    public void updateById(Datasource datasource) {
        if (datasource == null || StringUtils.isBlank(datasource.getId())) {
            return;
        }
        datasourceMapper.updateById(datasource);
    }

    public void deleteById(String id) {
        if (StringUtils.isBlank(id)) {
            return;
        }
        datasourceMapper.deleteById(StringUtils.trim(id));
    }
}
