<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <template #header>
        <div class="title">AIES 教学AI检查评价系统</div>
      </template>
      <el-form label-position="top" @submit.prevent="onSubmit">
        <el-form-item label="学号 / 工号">
          <el-input v-model="form.username" placeholder="请输入账号" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            autocomplete="current-password"
          />
        </el-form-item>
        <el-form-item label="图形验证码">
          <div class="captcha-row">
            <el-input v-model="form.captchaCode" placeholder="区分大小写" @keyup.enter="onSubmit" />
            <img
              v-if="captcha"
              :src="captcha.image"
              class="captcha-img"
              title="点击刷新"
              alt="图形验证码"
              @click="refreshCaptcha"
            />
          </div>
        </el-form-item>
        <el-button
          type="primary"
          class="submit-btn"
          :loading="loading"
          :disabled="cooldownSeconds > 0"
          @click="onSubmit"
        >
          {{ cooldownSeconds > 0 ? `操作过于频繁（${cooldownSeconds}s）` : '登 录' }}
        </el-button>
        <div class="links">
          <router-link to="/register">注册账号</router-link>
          <router-link to="/forgot-password">忘记密码</router-link>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { storeToRefs } from 'pinia'

const router = useRouter()
const auth = useAuthStore()
const { cooldownSeconds } = storeToRefs(auth)

const form = reactive({ username: '', password: '', captchaCode: '' })
const captcha = ref<{ captchaId: string; image: string } | null>(null)
const loading = ref(false)

async function refreshCaptcha() {
  captcha.value = await authApi.captcha()
  form.captchaCode = ''
}

async function onSubmit() {
  if (!form.username || !form.password || !form.captchaCode) {
    ElMessage.warning('请填写账号、密码与图形验证码')
    return
  }
  loading.value = true
  try {
    // 登录闭环：login存双token → /me装载userStore → 进首页（D-18两步走）
    const result = await authApi.login({
      username: form.username,
      password: form.password,
      captchaId: captcha.value?.captchaId ?? '',
      captchaCode: form.captchaCode
    })
    auth.setTokens(result)
    await auth.loadMe()
    ElMessage.success(`欢迎，${auth.me?.realName || form.username}`)
    router.push('/')
  } finally {
    loading.value = false
  }
}

onMounted(refreshCaptcha)
</script>

<style scoped>
.login-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f2d3d 0%, #2b4a6f 100%);
}
.login-card {
  width: 380px;
}
.title {
  text-align: center;
  font-size: 18px;
  font-weight: 600;
}
.captcha-row {
  display: flex;
  gap: 8px;
  width: 100%;
}
.captcha-img {
  height: 32px;
  cursor: pointer;
  border-radius: 4px;
  border: 1px solid #dcdfe6;
}
.submit-btn {
  width: 100%;
}
.links {
  display: flex;
  justify-content: space-between;
  margin-top: 12px;
  font-size: 13px;
}
.links a {
  color: var(--el-color-primary);
  text-decoration: none;
}
</style>
