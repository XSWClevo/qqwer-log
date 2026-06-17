<template>
  <div class="login-container">
    <el-card class="login-card">
      <template #header>
        <div class="login-header">
          <h2>qqwer</h2>
        </div>
      </template>

      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        class="login-form"
        @submit.prevent="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="用户名"
            size="large"
            prefix-icon="User"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="密码"
            size="large"
            prefix-icon="Lock"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="login-button"
            @click="handleLogin"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

// 表单引用
const loginFormRef = ref<FormInstance>()

// 登录表单数据
const loginForm = reactive({
  username: '',
  password: ''
})

// 表单验证规则
const loginRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不少于6位', trigger: 'blur' }
  ]
}

// 加载状态
const loading = ref(false)

// 登录处理
const handleLogin = async () => {
  if (!loginFormRef.value) return

  try {
    await loginFormRef.value.validate()
    loading.value = true

    const success = await authStore.login(loginForm)

    if (success) {
      const redirect = String(route.query.redirect || '/')
      router.push(redirect)
    }
  } catch (error) {
    console.error('登录验证失败:', error)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #007AFF 0%, #5856D6 100%);
  position: relative;
  overflow: hidden;

  // macOS 风格的装饰性背景
  &::before {
    content: '';
    position: absolute;
    width: 600px;
    height: 600px;
    background: radial-gradient(circle, rgba(255, 255, 255, 0.1) 0%, transparent 70%);
    top: -200px;
    right: -200px;
    border-radius: 50%;
  }

  &::after {
    content: '';
    position: absolute;
    width: 800px;
    height: 800px;
    background: radial-gradient(circle, rgba(255, 255, 255, 0.05) 0%, transparent 70%);
    bottom: -300px;
    left: -300px;
    border-radius: 50%;
  }
}

.login-card {
  width: 460px;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: saturate(180%) blur(30px);
  -webkit-backdrop-filter: saturate(180%) blur(30px);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 18px;
  box-shadow: 0 12px 48px rgba(0, 0, 0, 0.15);
  overflow: hidden;
  z-index: 1;
  animation: slideUp 0.6s cubic-bezier(0.4, 0.0, 0.2, 1);

  :deep(.el-card__header) {
    background: transparent;
    border-bottom: 1px solid rgba(0, 0, 0, 0.06);
    padding: 32px 40px 24px;
  }

  :deep(.el-card__body) {
    padding: 32px 40px 40px;
  }
}

.login-header {
  text-align: center;

  h2 {
    margin: 0;
    font-size: 28px;
    font-weight: 600;
    color: #1D1D1F;
    letter-spacing: -0.5px;
    font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Display', sans-serif;
  }
}

.login-form {
  margin-top: 0;

  :deep(.el-form-item) {
    margin-bottom: 24px;
  }

  :deep(.el-input__wrapper) {
    background: rgba(255, 255, 255, 0.9);
    border: 1px solid rgba(0, 0, 0, 0.1);
    border-radius: 10px;
    padding: 12px 16px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
    transition: all 0.25s cubic-bezier(0.4, 0.0, 0.2, 1);

    &:hover {
      border-color: rgba(0, 122, 255, 0.3);
      box-shadow: 0 2px 12px rgba(0, 122, 255, 0.08);
    }

    &.is-focus {
      border-color: #007AFF;
      box-shadow: 0 0 0 4px rgba(0, 122, 255, 0.1);
    }
  }

  :deep(.el-input__inner) {
    font-size: 15px;
    color: #1D1D1F;
    font-family: -apple-system, BlinkMacSystemFont, 'SF Pro Text', sans-serif;
  }
}

.login-button {
  width: 100%;
  height: 48px;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.3px;
  background: linear-gradient(135deg, #007AFF 0%, #0051D5 100%);
  border: none;
  border-radius: 10px;
  box-shadow: 0 4px 16px rgba(0, 122, 255, 0.3);
  transition: all 0.25s cubic-bezier(0.4, 0.0, 0.2, 1);

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 6px 24px rgba(0, 122, 255, 0.4);
  }

  &:active {
    transform: translateY(0);
  }
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(30px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
