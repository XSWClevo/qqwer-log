package cn.mw.loganalysis.jobcommunication.mapper;

import cn.mw.loganalysis.jobcommunication.entity.JobCommunicationRecord;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@DS("postgres")
public interface JobCommunicationRecordMapper extends BaseMapper<JobCommunicationRecord> {
}
