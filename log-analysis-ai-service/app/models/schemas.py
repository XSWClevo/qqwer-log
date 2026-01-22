from pydantic import BaseModel, Field
from typing import Optional, Any, List, Dict


class FieldInfo(BaseModel):
    """字段信息"""
    name: str = Field(..., description="字段名")
    type: str = Field(..., description="字段类型")
    label: str = Field(..., description="字段标签")
    isTimestamp: bool = Field(default=False, description="是否为时间戳字段")
    isStatsDimension: bool = Field(default=False, description="是否为统计维度")
    isContentField: bool = Field(default=False, description="是否为内容字段")


class TextToSQLRequest(BaseModel):
    """自然语言转SQL请求"""

    query: str = Field(..., description="自然语言查询", min_length=1, max_length=1000)
    table_name: str = Field(..., description="表名")
    table_schema: List[FieldInfo] = Field(..., description="表结构信息")
    datasource_type: str = Field(default="clickhouse", description="数据源类型")

    class Config:
        json_schema_extra = {
            "example": {
                "query": "查询最近1小时内severity为error的日志数量",
                "table_name": "syslog",
                "table_schema": [
                    {"name": "id", "type": "String", "label": "ID", "isTimestamp": False, "isStatsDimension": False, "isContentField": False},
                    {"name": "severity", "type": "String", "label": "严重级别", "isTimestamp": False, "isStatsDimension": True, "isContentField": False},
                    {"name": "timestamp", "type": "DateTime", "label": "时间戳", "isTimestamp": True, "isStatsDimension": False, "isContentField": False}
                ],
                "datasource_type": "clickhouse"
            }
        }


class TextToSQLResponse(BaseModel):
    """自然语言转SQL响应"""

    success: bool = Field(..., description="是否成功")
    sql: Optional[str] = Field(None, description="生成的SQL语句")
    error: Optional[str] = Field(None, description="错误信息")
    execution_time: Optional[float] = Field(None, description="执行时间(秒)")

    class Config:
        json_schema_extra = {
            "example": {
                "success": True,
                "sql": "SELECT COUNT(*) FROM syslog WHERE severity = 'error' AND timestamp >= now() - INTERVAL 1 HOUR",
                "error": None,
                "execution_time": 1.23
            }
        }


class HealthResponse(BaseModel):
    """健康检查响应"""

    status: str = Field(..., description="服务状态")
    version: str = Field(..., description="服务版本")
