<template>
  <!-- 登录表单（R-03 业务语义命名）：账号 + 密码 + 图形验证码 → 四态齐全
       载荷契约 LoginRequest{username,password,captchaId,captchaCode}；D-18 两步走逻辑不变 -->
  <el-form label-position="top" class="login-form" @submit.prevent="onSubmit">
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
      <CaptchaInput
        v-model:code="form.captchaCode"
        :image="captcha?.image ?? null"
        @refresh="refreshCaptcha"
        @submit="onSubmit"
      />
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
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { storeToRefs } from 'pinia'
import CaptchaInput from '@/components/ui/CaptchaInput.vue'
import { toast } from '@/utils/toast'

const emit = defineEmits<{ success: [realName: string] }>()
const auth = useAuthStore()
const { cooldownSeconds } = storeToRefs(auth)

const form = reactive({ username: '', password: '', captchaCode: '' })
const captcha = ref<{ captchaId: string; image: string } | null>(null)
const loading = ref(false)

async function refreshCaptcha() {
  try {
    captcha.value = await authApi.captcha()
    form.captchaCode = ''
  } catch {
    captcha.value = null
    toast.error('验证码获取失败', '点击"获取中…"重试')
  }
}

async function onSubmit() {
  if (!form.username || !form.password || !form.captchaCode) {
    toast.warning('请填写账号、密码与图形验证码')
    return
  }
  loading.value = true
  try {
    // 登录闭环：login存双token → /me装载userStore → 进首页（D-18两步走，逻辑不变）
    const result = await authApi.login({
      username: form.username,
      password: form.password,
      captchaId: captcha.value?.captchaId ?? '',
      captchaCode: form.captchaCode
    })
    auth.setTokens(result)
    await auth.loadMe()
    const name = auth.me?.realName || form.username
    toast.success(`欢迎，${name}`)
    emit('success', name)
  } catch {
    // 登录失败（账号密码错/验证码过期）：换新码让用户直接重试
    await refreshCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(refreshCaptcha)
</script>

<style scoped>
.submit-btn {
  width: 100%;
  margin-top: 4px;
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
.links a:hover {
  text-decoration: underline;
}
</style>
