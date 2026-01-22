from pydantic_settings import BaseSettings
from functools import lru_cache


class Settings(BaseSettings):
    """应用配置"""

    # Anthropic API配置
    anthropic_base_url: str = "https://api.anthropic.com"
    anthropic_api_key: str
    claude_model: str = "claude-3-5-sonnet-20241022"
    anthropic_default_opus_model: str = "claude-3-opus-20240229"
    anthropic_default_sonnet_model: str = "claude-3-5-sonnet-20241022"

    # 服务配置
    service_host: str = "0.0.0.0"
    service_port: int = 8001
    log_level: str = "INFO"

    # SQL Agent配置
    max_iterations: int = 3
    max_execution_time: int = 30
    max_result_rows: int = 1000

    class Config:
        env_file = ".env"
        case_sensitive = False


@lru_cache()
def get_settings() -> Settings:
    """获取配置单例"""
    return Settings()
