package cn.mw.loganalysis.alert.mapper;

import cn.mw.loganalysis.alert.entity.AlertMonitorState;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
@DS("postgres")
public interface AlertMonitorStateMapper extends BaseMapper<AlertMonitorState> {

    @Insert("""
            INSERT INTO alert_monitor_states (
                rule_id,
                datasource_id,
                table_name,
                group_key,
                group_values,
                state,
                previous_state,
                last_value,
                last_threshold,
                last_evaluated_at,
                last_state_changed_at,
                last_notified_at,
                renotify_count,
                no_data_since,
                updated_at
            ) VALUES (
                #{state.ruleId},
                #{state.datasourceId},
                #{state.tableName},
                #{state.groupKey},
                #{state.groupValues,typeHandler=cn.mw.loganalysis.common.handler.PostgresJsonbTypeHandler},
                #{state.state},
                #{state.previousState},
                #{state.lastValue},
                #{state.lastThreshold},
                #{state.lastEvaluatedAt},
                #{state.lastStateChangedAt},
                #{state.lastNotifiedAt},
                #{state.renotifyCount},
                #{state.noDataSince},
                #{state.updatedAt}
            )
            ON CONFLICT (rule_id, datasource_id, table_name, group_key)
            DO UPDATE SET
                group_values = EXCLUDED.group_values,
                state = EXCLUDED.state,
                previous_state = EXCLUDED.previous_state,
                last_value = EXCLUDED.last_value,
                last_threshold = EXCLUDED.last_threshold,
                last_evaluated_at = EXCLUDED.last_evaluated_at,
                last_state_changed_at = EXCLUDED.last_state_changed_at,
                last_notified_at = EXCLUDED.last_notified_at,
                renotify_count = EXCLUDED.renotify_count,
                no_data_since = EXCLUDED.no_data_since,
                updated_at = EXCLUDED.updated_at
            """)
    int upsertByTarget(@Param("state") AlertMonitorState state);
}
