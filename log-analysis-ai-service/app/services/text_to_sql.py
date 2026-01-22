import time
import logging
from typing import Dict, Any, Optional, List
from langchain_anthropic import ChatAnthropic
from app.config import Settings

logger = logging.getLogger(__name__)


class TextToSQLService:
    """自然语言转SQL服务（只生成SQL，不执行）"""

    def __init__(self, settings: Settings):
        self.settings = settings
        self._llm: Optional[ChatAnthropic] = None
        self._initialize()

    def _initialize(self):
        """初始化LangChain组件"""
        try:
            # 初始化Claude
            logger.info(f"初始化Claude API，模型: {self.settings.claude_model}")
            logger.info(f"使用Base URL: {self.settings.anthropic_base_url}")
            self._llm = ChatAnthropic(
                model=self.settings.claude_model,
                temperature=0,  # 确保SQL生成的确定性
                anthropic_api_key=self.settings.anthropic_api_key,
                anthropic_api_url=self.settings.anthropic_base_url,
                max_tokens=4096
            )

            logger.info("TextToSQL服务初始化成功")

        except Exception as e:
            logger.error(f"TextToSQL服务初始化失败: {e}")
            raise

    def generate_sql(
        self,
        natural_language: str,
        table_name: str,
        table_schema: List[Dict[str, Any]],
        datasource_type: str = "clickhouse"
    ) -> Dict[str, Any]:
        """
        自然语言转SQL（只生成，不执行）

        Args:
            natural_language: 自然语言查询
            table_name: 表名
            table_schema: 表结构信息
            datasource_type: 数据源类型

        Returns:
            包含SQL和执行时间的字典
        """
        start_time = time.time()

        try:
            # 构建提示词
            prompt = self._build_prompt(
                natural_language,
                table_name,
                table_schema,
                datasource_type
            )

            logger.info(f"生成SQL查询: {natural_language}")
            logger.debug(f"表名: {table_name}, 数据源类型: {datasource_type}")

            # 调用Claude生成SQL
            response = self._llm.invoke(prompt)
            sql = response.content.strip()

            # 清理SQL（移除markdown代码块标记）
            sql = self._clean_sql(sql)

            execution_time = time.time() - start_time

            logger.info(f"SQL生成成功，耗时: {execution_time:.2f}秒")
            logger.debug(f"生成的SQL: {sql}")

            return {
                "success": True,
                "sql": sql,
                "error": None,
                "execution_time": round(execution_time, 2)
            }

        except Exception as e:
            execution_time = time.time() - start_time
            error_msg = str(e)
            logger.error(f"SQL生成失败: {error_msg}")

            return {
                "success": False,
                "sql": None,
                "error": error_msg,
                "execution_time": round(execution_time, 2)
            }

    def _build_prompt(
        self,
        natural_language: str,
        table_name: str,
        table_schema: List[Dict[str, Any]],
        datasource_type: str
    ) -> str:
        """
        构建提示词

        Args:
            natural_language: 用户的自然语言查询
            table_name: 表名
            table_schema: 表结构信息
            datasource_type: 数据源类型

        Returns:
            提示词
        """
        # 构建字段描述
        fields_desc = []
        timestamp_field = None
        stats_dimensions = []
        content_fields = []

        for field in table_schema:
            field_name = field.get('name', '')
            field_type = field.get('type', '')
            field_label = field.get('label', field_name)

            fields_desc.append(f"- {field_name} ({field_type}): {field_label}")

            if field.get('isTimestamp'):
                timestamp_field = field_name
            if field.get('isStatsDimension'):
                stats_dimensions.append(field_name)
            if field.get('isContentField'):
                content_fields.append(field_name)

        fields_text = "\n".join(fields_desc)

        # 根据数据源类型调整SQL语法
        syntax_hints = self._get_syntax_hints(datasource_type)

        prompt = f"""你是一个{datasource_type.upper()}数据库SQL专家。请根据用户的自然语言需求生成SQL查询语句。

表名: {table_name}

表结构:
{fields_text}

字段说明:
- 时间戳字段: {timestamp_field or '未指定'}
- 统计维度字段: {', '.join(stats_dimensions) if stats_dimensions else '无'}
- 内容字段: {', '.join(content_fields) if content_fields else '无'}

用户需求: {natural_language}

{syntax_hints}

重要约束:
1. 只能查询 {table_name} 表
2. 只能生成 SELECT 查询语句，不允许 DROP、DELETE、UPDATE、INSERT 等操作
3. 查询结果限制在 {self.settings.max_result_rows} 条以内，使用 LIMIT 子句
4. 如果涉及时间范围，使用 {timestamp_field or 'timestamp'} 字段
5. 只返回SQL语句，不要包含任何解释或markdown标记
6. SQL语句必须是完整的、可执行的

请直接返回SQL语句:"""

        return prompt

    def _get_syntax_hints(self, datasource_type: str) -> str:
        """获取数据源特定的SQL语法提示"""
        hints = {
            "clickhouse": """
ClickHouse语法提示:
- 时间函数: now(), toStartOfHour(), toStartOfDay(), INTERVAL 1 HOUR
- 字符串匹配: LIKE, ILIKE (不区分大小写)
- 聚合函数: COUNT(), SUM(), AVG(), MAX(), MIN()
- 排序: ORDER BY ... DESC/ASC
- 限制: LIMIT N""",

            "postgresql": """
PostgreSQL语法提示:
- 时间函数: NOW(), CURRENT_TIMESTAMP, INTERVAL '1 hour'
- 字符串匹配: LIKE, ILIKE (不区分大小写)
- 聚合函数: COUNT(), SUM(), AVG(), MAX(), MIN()
- 排序: ORDER BY ... DESC/ASC
- 限制: LIMIT N""",

            "elasticsearch": """
Elasticsearch SQL语法提示:
- 时间函数: NOW(), INTERVAL
- 字符串匹配: LIKE, MATCH, QUERY
- 聚合函数: COUNT(), SUM(), AVG(), MAX(), MIN()
- 排序: ORDER BY ... DESC/ASC
- 限制: LIMIT N""",

            "mysql": """
MySQL语法提示:
- 时间函数: NOW(), DATE_SUB(), INTERVAL 1 HOUR
- 字符串匹配: LIKE
- 聚合函数: COUNT(), SUM(), AVG(), MAX(), MIN()
- 排序: ORDER BY ... DESC/ASC
- 限制: LIMIT N"""
        }

        return hints.get(datasource_type.lower(), hints["clickhouse"])

    def _clean_sql(self, sql: str) -> str:
        """
        清理SQL语句（移除markdown标记等）

        Args:
            sql: 原始SQL

        Returns:
            清理后的SQL
        """
        # 移除markdown代码块标记
        sql = sql.replace("```sql", "").replace("```", "")

        # 移除前后空白
        sql = sql.strip()

        # 如果有多行，只取第一个完整的SQL语句
        lines = sql.split('\n')
        cleaned_lines = []
        for line in lines:
            line = line.strip()
            if line and not line.startswith('--') and not line.startswith('#'):
                cleaned_lines.append(line)

        sql = ' '.join(cleaned_lines)

        return sql

    def test_connection(self) -> bool:
        """
        测试服务是否正常

        Returns:
            是否正常
        """
        try:
            return self._llm is not None
        except Exception as e:
            logger.error(f"服务测试失败: {e}")
            return False
