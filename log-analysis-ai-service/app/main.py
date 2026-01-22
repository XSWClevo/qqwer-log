from fastapi import FastAPI, HTTPException, Depends
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager
import logging
from app.config import get_settings, Settings
from app.models import TextToSQLRequest, TextToSQLResponse, HealthResponse
from app.services import TextToSQLService

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# 全局服务实例
text_to_sql_service: TextToSQLService = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    """应用生命周期管理"""
    global text_to_sql_service

    # 启动时初始化
    logger.info("启动 AI Query Service")
    settings = get_settings()

    try:
        text_to_sql_service = TextToSQLService(settings)
        logger.info("服务初始化成功")
    except Exception as e:
        logger.error(f"服务初始化失败: {e}")
        raise

    yield

    # 关闭时清理
    logger.info("关闭 AI Query Service")


# 创建FastAPI应用
app = FastAPI(
    title="Log Analysis AI Query Service",
    description="基于LangChain和Claude的自然语言转SQL服务",
    version="1.0.0",
    lifespan=lifespan
)

# 配置CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # 生产环境应该限制具体域名
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


def get_text_to_sql_service() -> TextToSQLService:
    """获取TextToSQL服务实例"""
    if text_to_sql_service is None:
        raise HTTPException(status_code=503, detail="服务未初始化")
    return text_to_sql_service


@app.get("/", response_model=HealthResponse)
async def root():
    """根路径"""
    return HealthResponse(
        status="running",
        version="1.0.0"
    )


@app.get("/health", response_model=HealthResponse)
async def health_check(
    service: TextToSQLService = Depends(get_text_to_sql_service)
):
    """健康检查"""
    # 测试数据库连接
    is_healthy = service.test_connection()

    if not is_healthy:
        raise HTTPException(status_code=503, detail="数据库连接失败")

    return HealthResponse(
        status="healthy",
        version="1.0.0"
    )


@app.post("/text-to-sql", response_model=TextToSQLResponse)
async def text_to_sql(
    request: TextToSQLRequest,
    service: TextToSQLService = Depends(get_text_to_sql_service)
):
    """
    自然语言转SQL接口（只生成SQL，不执行）

    Args:
        request: 包含自然语言查询、表名和表结构的请求

    Returns:
        包含SQL和执行时间的响应
    """
    try:
        logger.info(f"收到查询请求: {request.query}")
        logger.debug(f"表名: {request.table_name}, 数据源类型: {request.datasource_type}")

        # 将Pydantic模型转换为字典
        table_schema = [field.model_dump() for field in request.table_schema]

        # 生成SQL
        result = service.generate_sql(
            natural_language=request.query,
            table_name=request.table_name,
            table_schema=table_schema,
            datasource_type=request.datasource_type
        )

        return TextToSQLResponse(**result)

    except Exception as e:
        logger.error(f"处理请求失败: {e}")
        raise HTTPException(status_code=500, detail=str(e))


if __name__ == "__main__":
    import uvicorn

    settings = get_settings()
    uvicorn.run(
        "app.main:app",
        host=settings.service_host,
        port=settings.service_port,
        reload=True,
        log_level=settings.log_level.lower()
    )
