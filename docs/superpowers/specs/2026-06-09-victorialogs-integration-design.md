# VictoriaLogs Integration Design

## Goal

Add VictoriaLogs as a first-class log storage target for the existing Vector pipeline, and make it queryable from the platform's existing log search flow.

The first implementation phase covers:

- Vector -> VictoriaLogs ingestion.
- VictoriaLogs datasource registration and connection testing.
- Queryable VictoriaLogs sinks.
- Platform log search against VictoriaLogs.
- Log context, dimension statistics, and time-series count queries for VictoriaLogs.

This phase does not cover:

- AI natural-language querying for VictoriaLogs.
- Alert monitor evaluation against VictoriaLogs.
- Attack classification dataset reading from VictoriaLogs.
- Replacing existing ClickHouse log storage.
- VictoriaMetrics or VictoriaTraces integration.

## Current Context

The project already has a Vector management flow:

- Vector component templates are initialized through PostgreSQL Liquibase.
- Vector Agent pulls generated Vector configuration from the backend.
- ClickHouse sink templates can be marked `queryable=true`.
- Queryable sinks are exposed as log datasources to the platform.

The project also already has datasource/query abstractions:

- `LogQueryStrategy` supports multiple query backends.
- `DatasourceOperationStrategy` supports connection tests and backend-specific operations.
- Existing implementations cover ClickHouse, Elasticsearch, and PostgreSQL patterns.
- The frontend already reads queryable Vector sink components.

VictoriaLogs should use these extension points instead of changing ClickHouse-specific logic in place.

## Recommended Approach

Use a Vector `http` sink to write JSON line events to VictoriaLogs.

Reasons:

- VictoriaLogs supports direct HTTP ingestion.
- The HTTP sink gives explicit control over field names and query parameters.
- The platform can map existing normalized fields to VictoriaLogs conventions.
- It avoids Elasticsearch sink defaults leaking into the platform model.

The Elasticsearch-compatible ingestion path remains a future fallback if HTTP ingestion exposes unexpected compatibility issues.

## Data Flow

```text
Log sources
  -> Vector source components
  -> Vector remap/normalization transforms
  -> Vector http sink
  -> VictoriaLogs /insert/jsonline endpoint
  -> Platform VictoriaLogsQueryStrategy
  -> Existing log query APIs and frontend views
```

ClickHouse remains supported in parallel:

```text
Log sources
  -> Vector transforms
  -> ClickHouse sink
  -> Existing ClickHouseQueryStrategy
```

## Field Mapping

The platform should normalize events before writing to VictoriaLogs:

| Platform field | VictoriaLogs role |
| --- | --- |
| `timestamp` | `_time` |
| `message` or `raw` | `_msg` |
| `machine_id` | stream field |
| `file_name` | stream field |
| `source_type` | stream field |
| `app` | stream field when present |
| `env` | stream field when present |
| Other parsed fields | regular log fields |

The default stream fields should be:

```text
machine_id,file_name,source_type,app,env
```

The sink UI should allow editing this list because not every pipeline has all five fields.

## Backend Changes

### Datasource Type

Add a new datasource type:

```text
victorialogs
```

The connection config should support:

- `endpoint`, for example `http://localhost:9428`.
- Optional username and password.
- Optional tenant/account identifier if the deployment later uses multitenancy.
- Default query timeout.

The default port is `9428`.

### Operation Strategy

Add `VictoriaLogsOperationStrategy implements DatasourceOperationStrategy`.

Responsibilities:

- Test connection by calling a VictoriaLogs health or lightweight query endpoint.
- Return a recommended schema that maps to the platform log view fields.
- Return a clear unsupported result for table creation because VictoriaLogs does not require ClickHouse-style DDL.
- Return stream or field hints when available.

### Query Strategy

Add `VictoriaLogsQueryStrategy implements LogQueryStrategy`.

It should support:

- `getSupportedType()` returns `victorialogs`.
- `getTableSchema()` returns known platform fields plus fields discovered from recent logs when feasible.
- `queryLogs()` maps platform time range, keyword conditions, and field filters to LogsQL.
- `queryLogContext()` queries before and after the selected timestamp with the same stream/filter constraints.
- `queryStats()` returns top values for a selected dimension.
- `queryTimeSeries()` returns count buckets for the selected time range.
- `executeRawSQL()` rejects the request with a clear message because VictoriaLogs uses LogsQL, not SQL.

The strategy must normalize VictoriaLogs responses to the same shape expected by existing frontend log views.

### Config Parsing

Extend `DynamicLogQueryService` to parse VictoriaLogs sink YAML into `DatasourceConnectionConfig`.

The parser should recognize:

- `type: http`
- VictoriaLogs endpoint URL.
- Query parameters such as `_stream_fields`, `_msg_field`, and `_time_field`.
- Authentication configuration if present.

If the sink template uses a project-level `vector_type` of `victorialogs`, the parsed connection config type should be `victorialogs` even though the generated Vector sink type is `http`.

### Vector Component Template

Add a PostgreSQL Liquibase changeset that inserts a VictoriaLogs sink template into `vector_config_components`.

Template requirements:

- `component_type = sink`
- `vector_type = victorialogs`
- `queryable = true`
- `display_name = VictoriaLogs`
- The generated Vector YAML uses `type: http`.
- The default endpoint targets `/insert/jsonline`.
- The template exposes editable visual metadata for endpoint, stream fields, message field, time field, encoding, compression, auth, and buffer settings.

The changeset must be idempotent with `ON CONFLICT`.

### YAML Generation

Extend `ComponentYamlGeneratorService` to generate VictoriaLogs sink YAML.

The YAML should include:

- `type: http`
- `uri: <endpoint>/insert/jsonline?...`
- `method: post`
- JSON encoding.
- Batch and buffer defaults consistent with other sinks.
- Optional basic auth when username/password is configured.

The generator must preserve existing ClickHouse, console, and Elasticsearch behavior.

## Frontend Changes

### Component Library

Add `victorialogs` to sink component type options.

The form should expose:

- Endpoint.
- Stream fields.
- Message field.
- Time field.
- Optional username and password.
- Compression.
- Batch timeout and max bytes.
- Buffer settings.

The UI should not expose ClickHouse table, database, engine, partition, or DDL settings for VictoriaLogs.

### Visual Config Editor

Add a VictoriaLogs sink config panel.

The panel should follow existing Vector UI patterns and avoid introducing a separate visual style.

### Datasource Management

Allow `victorialogs` in datasource creation and testing.

For VictoriaLogs, the UI should label the storage target as log search storage rather than SQL storage.

### Log Query Views

Existing log query views should work through the backend query strategy. UI changes should be limited to capability checks:

- Hide raw SQL execution for VictoriaLogs.
- Hide ClickHouse table/DDL-specific controls.
- Keep time range, keyword search, field filters, context, stats, and time-series views available.

## Error Handling

The integration should fail with clear messages:

- Connection test failure should report the endpoint and HTTP status when available.
- Query errors should include a concise VictoriaLogs error message.
- Unsupported raw SQL should say VictoriaLogs uses LogsQL, not SQL.
- Missing `_msg` or `_time` mapping should surface as a configuration error before deployment.
- Vector sink generation should reject empty endpoint or empty stream field configuration.

## Testing

Backend tests should cover:

- VictoriaLogs config parsing.
- VictoriaLogs query strategy request construction.
- Query response normalization.
- Unsupported `executeRawSQL()`.
- Vector YAML generation for VictoriaLogs sink.

Frontend checks should cover:

- VictoriaLogs appears as a sink option.
- VictoriaLogs form does not show ClickHouse-only fields.
- Queryable VictoriaLogs sinks appear in datasource selection.

Manual verification should cover:

- Start VictoriaLogs locally.
- Generate or create a Vector VictoriaLogs sink.
- Send sample logs through Vector.
- Query logs from the platform.
- Query context, top dimension values, and time-series counts.
- Confirm existing ClickHouse query flow still works.

## Rollout

Roll out as an additive feature:

1. Add VictoriaLogs service and datasource support.
2. Add Vector sink template and YAML generation.
3. Add query strategy.
4. Add frontend sink configuration.
5. Validate with dual-write from a test pipeline.
6. Keep ClickHouse as the default production storage until VictoriaLogs is proven stable.

## Out Of Scope Follow-Ups

After the first phase is stable, separate designs can cover:

- Text-to-LogsQL support in the AI service.
- VictoriaLogs-backed alert monitors.
- VictoriaLogs attack dataset readers.
- Migration or retention policy from ClickHouse to VictoriaLogs.
- VictoriaMetrics for metrics.
- VictoriaTraces for distributed tracing.
