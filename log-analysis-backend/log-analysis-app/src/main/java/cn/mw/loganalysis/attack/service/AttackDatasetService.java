package cn.mw.loganalysis.attack.service;

import cn.mw.loganalysis.attack.converter.AttackStructMapper;
import cn.mw.loganalysis.attack.dto.AttackDatasetQueryRequest;
import cn.mw.loganalysis.attack.dto.CreateAttackDatasetRequest;
import cn.mw.loganalysis.attack.dto.UpdateAttackDatasetRequest;
import cn.mw.loganalysis.attack.entity.AttackLogDataset;
import cn.mw.loganalysis.attack.mapper.AttackLogDatasetMapper;
import cn.mw.loganalysis.common.exception.ResourceNotFoundException;
import cn.mw.loganalysis.common.exception.ValidationException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttackDatasetService extends ServiceImpl<AttackLogDatasetMapper, AttackLogDataset> {

    private static final List<String> REQUIRED_STANDARD_FIELDS = List.of("timestamp", "message");

    private final AttackStructMapper attackStructMapper;

    @Transactional(rollbackFor = Exception.class)
    public AttackLogDataset create(CreateAttackDatasetRequest request) {
        validateDatasetTarget(request.getDatasourceType(), request.getTableName(), request.getIndexName());
        validateFieldMapping(request.getFieldMapping());

        AttackLogDataset dataset = attackStructMapper.toDataset(request);
        getBaseMapper().insert(dataset);
        return dataset;
    }

    @Transactional(rollbackFor = Exception.class)
    public AttackLogDataset update(Long id, UpdateAttackDatasetRequest request) {
        AttackLogDataset dataset = requireDataset(id);
        if (request.getFieldMapping() != null) {
            validateFieldMapping(request.getFieldMapping());
        }
        attackStructMapper.updateDataset(request, dataset);
        validateDatasetTarget(dataset.getDatasourceType(), dataset.getTableName(), dataset.getIndexName());
        getBaseMapper().updateById(dataset);
        return dataset;
    }

    public AttackLogDataset get(Long id) {
        return requireDataset(id);
    }

    public Page<AttackLogDataset> list(AttackDatasetQueryRequest request) {
        AttackDatasetQueryRequest normalizedRequest = ObjectUtils.defaultIfNull(request, new AttackDatasetQueryRequest());
        Page<AttackLogDataset> page = new Page<>(
                Math.max(ObjectUtils.defaultIfNull(normalizedRequest.getPageNum(), 1), 1),
                Math.max(ObjectUtils.defaultIfNull(normalizedRequest.getPageSize(), 20), 1));

        LambdaQueryWrapper<AttackLogDataset> wrapper = new LambdaQueryWrapper<>();
        String keyword = StringUtils.trimToEmpty(normalizedRequest.getKeyword());
        if (StringUtils.isNotBlank(keyword)) {
            wrapper.and(query -> query.like(AttackLogDataset::getName, keyword)
                    .or()
                    .like(AttackLogDataset::getTableName, keyword)
                    .or()
                    .like(AttackLogDataset::getIndexName, keyword));
        }
        wrapper.eq(StringUtils.isNotBlank(normalizedRequest.getDatasourceType()),
                AttackLogDataset::getDatasourceType, StringUtils.trim(normalizedRequest.getDatasourceType()));
        wrapper.eq(ObjectUtils.isNotEmpty(normalizedRequest.getEnabled()),
                AttackLogDataset::getEnabled, normalizedRequest.getEnabled());
        wrapper.orderByDesc(AttackLogDataset::getUpdatedAt).orderByDesc(AttackLogDataset::getId);
        return getBaseMapper().selectPage(page, wrapper);
    }

    public List<AttackLogDataset> listEnabled() {
        return getBaseMapper().selectList(new LambdaQueryWrapper<AttackLogDataset>()
                .eq(AttackLogDataset::getEnabled, true)
                .orderByAsc(AttackLogDataset::getId));
    }

    public List<AttackLogDataset> listByIds(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return List.of();
        }
        return getBaseMapper().selectList(new LambdaQueryWrapper<AttackLogDataset>()
                .in(AttackLogDataset::getId, ids)
                .orderByAsc(AttackLogDataset::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireDataset(id);
        getBaseMapper().deleteById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateCursor(AttackLogDataset dataset, LocalDateTime timestamp, String fingerprint) {
        if (ObjectUtils.isEmpty(dataset) || ObjectUtils.isEmpty(dataset.getId()) || ObjectUtils.isEmpty(timestamp)) {
            return;
        }
        dataset.setScanCursorTimestamp(timestamp);
        dataset.setScanCursorFingerprint(StringUtils.trimToEmpty(fingerprint));
        dataset.setUpdatedAt(LocalDateTime.now());
        getBaseMapper().updateById(dataset);
    }

    private AttackLogDataset requireDataset(Long id) {
        AttackLogDataset dataset = getBaseMapper().selectById(id);
        if (ObjectUtils.isEmpty(dataset)) {
            throw new ResourceNotFoundException("攻击分类数据集不存在: " + id);
        }
        return dataset;
    }

    private void validateDatasetTarget(String datasourceType, String tableName, String indexName) {
        if (StringUtils.isBlank(datasourceType)) {
            throw new ValidationException("数据源类型不能为空");
        }
        if (StringUtils.equalsIgnoreCase(datasourceType, "clickhouse") && StringUtils.isBlank(tableName)) {
            throw new ValidationException("ClickHouse 数据集必须配置 tableName");
        }
        if (StringUtils.equalsIgnoreCase(datasourceType, "elasticsearch") && StringUtils.isBlank(indexName)) {
            throw new ValidationException("Elasticsearch 数据集必须配置 indexName");
        }
    }

    private void validateFieldMapping(Map<String, String> fieldMapping) {
        if (MapUtils.isEmpty(fieldMapping)) {
            throw new ValidationException("字段映射不能为空");
        }
        for (String field : REQUIRED_STANDARD_FIELDS) {
            if (StringUtils.isBlank(fieldMapping.get(field))) {
                throw new ValidationException("字段映射缺少必需标准字段: " + field);
            }
        }
    }
}
