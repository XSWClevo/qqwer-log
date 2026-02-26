package cn.mw.loganalysis.logsource.mapper;

import cn.mw.loganalysis.logsource.entity.TrustedLogSource;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 可信任日志源 Mapper
 *
 * @author Claude
 * @since 2026-01-23
 */
@Mapper
public interface TrustedLogSourceMapper extends BaseMapper<TrustedLogSource> {

    /**
     * 根据 IP 地址查询
     */
    default TrustedLogSource selectByIp(String sourceIp) {
        LambdaQueryWrapper<TrustedLogSource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrustedLogSource::getSourceIp, sourceIp);
        return selectOne(wrapper);
    }

    /**
     * 查询所有信任的日志源
     */
    default List<TrustedLogSource> selectTrustedSources() {
        LambdaQueryWrapper<TrustedLogSource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrustedLogSource::getStatus, "trusted")
               .orderByDesc(TrustedLogSource::getLastSeenAt);
        return selectList(wrapper);
    }

    /**
     * 查询待审核的日志源
     */
    default List<TrustedLogSource> selectPendingSources() {
        LambdaQueryWrapper<TrustedLogSource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrustedLogSource::getStatus, "pending")
               .orderByDesc(TrustedLogSource::getFirstSeenAt);
        return selectList(wrapper);
    }

    /**
     * 查询被拉黑的日志源
     */
    default List<TrustedLogSource> selectBlockedSources() {
        LambdaQueryWrapper<TrustedLogSource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrustedLogSource::getStatus, "blocked")
               .orderByDesc(TrustedLogSource::getUpdatedAt);
        return selectList(wrapper);
    }

    /**
     * 根据状态查询
     */
    default List<TrustedLogSource> selectByStatus(String status) {
        LambdaQueryWrapper<TrustedLogSource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrustedLogSource::getStatus, status)
               .orderByDesc(TrustedLogSource::getLastSeenAt);
        return selectList(wrapper);
    }
}
