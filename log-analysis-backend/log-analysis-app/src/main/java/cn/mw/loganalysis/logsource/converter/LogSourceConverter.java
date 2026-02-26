package cn.mw.loganalysis.logsource.converter;

import cn.mw.loganalysis.logsource.dto.LogSourceDTO;
import cn.mw.loganalysis.logsource.entity.TrustedLogSource;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 日志源对象转换器
 *
 * @author Claude
 * @since 2026-01-23
 */
@Mapper(componentModel = "spring")
public interface LogSourceConverter {

    /**
     * Entity → DTO
     */
    LogSourceDTO toDTO(TrustedLogSource entity);

    /**
     * Entity List → DTO List
     */
    List<LogSourceDTO> toDTOList(List<TrustedLogSource> entities);
}
