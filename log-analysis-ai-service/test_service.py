#!/usr/bin/env python3
"""
测试脚本：验证服务配置和启动
不需要真实的API Key
"""

import sys
import os

# 添加app目录到路径
sys.path.insert(0, os.path.dirname(__file__))

def test_config():
    """测试配置加载"""
    print("=" * 60)
    print("测试1: 配置加载")
    print("=" * 60)

    try:
        from app.config import get_settings

        # 临时设置一个假的API Key用于测试
        os.environ['ANTHROPIC_API_KEY'] = 'test-key-for-validation'

        settings = get_settings()
        print(f"✅ 配置加载成功")
        print(f"   - Claude模型: {settings.claude_model}")
        print(f"   - 服务端口: {settings.service_port}")
        print(f"   - 日志级别: {settings.log_level}")
        print(f"   - 最大迭代次数: {settings.max_iterations}")
        print(f"   - 最大执行时间: {settings.max_execution_time}秒")
        print(f"   - 最大结果行数: {settings.max_result_rows}")
        return True
    except Exception as e:
        print(f"❌ 配置加载失败: {e}")
        return False

def test_imports():
    """测试依赖导入"""
    print("\n" + "=" * 60)
    print("测试2: 依赖导入")
    print("=" * 60)

    dependencies = [
        ('fastapi', 'FastAPI'),
        ('uvicorn', 'Uvicorn'),
        ('langchain_anthropic', 'LangChain Anthropic'),
        ('pydantic', 'Pydantic'),
        ('pydantic_settings', 'Pydantic Settings'),
        ('dotenv', 'Python Dotenv'),
    ]

    all_ok = True
    for module, name in dependencies:
        try:
            __import__(module)
            print(f"✅ {name}")
        except ImportError as e:
            print(f"❌ {name}: {e}")
            all_ok = False

    return all_ok

def test_app_structure():
    """测试应用结构"""
    print("\n" + "=" * 60)
    print("测试3: 应用结构")
    print("=" * 60)

    try:
        from app.models import TextToSQLRequest, TextToSQLResponse, HealthResponse
        print("✅ 模型定义正确")

        from app.services import TextToSQLService
        print("✅ 服务定义正确")

        from app.main import app
        print("✅ FastAPI应用定义正确")

        return True
    except Exception as e:
        print(f"❌ 应用结构检查失败: {e}")
        import traceback
        traceback.print_exc()
        return False

def main():
    """主测试函数"""
    print("\n🚀 Python AI服务测试\n")

    results = []

    # 测试1: 配置
    results.append(("配置加载", test_config()))

    # 测试2: 依赖
    results.append(("依赖导入", test_imports()))

    # 测试3: 应用结构
    results.append(("应用结构", test_app_structure()))

    # 总结
    print("\n" + "=" * 60)
    print("测试总结")
    print("=" * 60)

    for name, result in results:
        status = "✅ 通过" if result else "❌ 失败"
        print(f"{name}: {status}")

    all_passed = all(r for _, r in results)

    if all_passed:
        print("\n🎉 所有测试通过！服务可以启动。")
        print("\n下一步:")
        print("1. 配置真实的ANTHROPIC_API_KEY到.env文件")
        print("2. 运行: python -m app.main")
        return 0
    else:
        print("\n❌ 部分测试失败，请检查错误信息。")
        return 1

if __name__ == "__main__":
    sys.exit(main())
