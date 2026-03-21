package cn.mw.loganalysis.logsource.mapper;

import cn.mw.loganalysis.logsource.entity.TrustedLogSource;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 可信任日志源 Mapper
 *
 * @author Claude
 * @since 2026-01-23
 */
@Mapper
public interface TrustedLogSourceMapper extends BaseMapper<TrustedLogSource> {
}
