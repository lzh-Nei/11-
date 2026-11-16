<template>
  <div class="register-wrap">
    <el-card class="register-card">
      <template #header>
        <div class="title">AIES 注册账号</div>
      </template>
      <el-form label-position="top" @submit.prevent="onSubmit">
        <el-form-item label="学号">
          <el-input v-model="form.username" placeholder="4-20位字母、数字或下划线" autocomplete="username" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.realName" placeholder="选填，默认取学号" maxlength="32" />
        </el-form-item>
        <el-form-item label="绑定邮箱">
          <el-input v-model="form.email" placeholder="用于登录验证与找回密码" />
        </el-form-item>
        <el-form-item label="班级码">
          <el-input v-model="form.classCode" placeholder="由授课教师发放" />
        </el-form-item>
        <el-form-item label="初始密码">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="至少8位，同时含字母和数字"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>
        <el-form-item label="邮箱验证码">
          <div class="code-row">
            <el-input v-model="form.emailCode" placeholder="6位数字" @keyup.enter="onSubmit" />
            <el-button :loading="sending" :disabled="sendDisabled" @click="onSendCode">
              {{ sendButtonText }}
            </el-button>
          </div>
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
        <el-button type="primary" class="submit-btn" :loading="loading" @click="onSubmit">注 册</el-button>
        <div class="links">
          <router-link to="/login">已有账号？返回登录</router-link>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { storeToRefs } from 'pinia'

const router = useRouter()
const auth = useAuthStore()
const { cooldownSeconds } = storeToRefs(auth)

const form = reactive({
  username: '',
  realName: '',
  email: '',
  classCode: '',
  password: '',
  emailCode: '',
  captchaCode: ''
})
const captcha = ref<{ captchaId: string; image: string } | null>(null)
const loading = ref(false)
const sending = ref(false)
const countdown = ref(0)

let timer: number | undefined

async function refreshCaptcha() {
  captcha.value = await authApi.captcha()
  form.captchaCode = ''
}

/** 发送注册验证码（A-06 scene=REGISTER）：图形码一次性，成功后刷新待注册提交再验 */
async function onSendCode() {
  if (!form.username || !form.email || !form.captchaCode) {
    ElMessage.warning('请先填写学号、邮箱与图形验证码')
    return
  }
  sending.value = true
  try {
    const result = await authApi.emailCode({
      username: form.username,
      email: form.email,
      scene: 'REGISTER',
      captchaId: captcha.value?.captchaId ?? '',
      captchaCode: form.captchaCode
    })
    // dev模拟模式（未配SMTP）回显mockCode便于联调；真实SMTP通道为null
    ElMessage.success(
      result.mockCode ? `验证码已发送（模拟模式：${result.mockCode}）` : '验证码已发送，请查收邮箱'
    )
    startCountdown()
    await refreshCaptcha()
  } finally {
    sending.value = false
  }
}

/** 60s防重发倒计时（后端单邮箱冷却60s，S-12频控） */
function startCountdown() {
  countdown.value = 60
  if (timer !== undefined) window.clearInterval(timer)
  timer = window.setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      window.clearInterval(timer)
      timer = undefined
    }
  }, 1000)
}

const sendDisabled = computed(() => countdown.value > 0 || cooldownSeconds.value > 0)
const sendButtonText = computed(() => {
  if (countdown.value > 0) return `${countdown.value}s后可重发`
  if (cooldownSeconds.value > 0) return `操作过于频繁（${cooldownSeconds.value}s）`
  return '发送邮箱验证码'
})

/** 提交注册（A-08）：学号重复→30012、班级码无效→30011、弱密码→30013由拦截器统一提示 */
async function onSubmit() {
  if (
    !form.username ||
    !form.email ||
    !form.classCode ||
    !form.password ||
    !form.emailCode ||
    !form.captchaCode
  ) {
    ElMessage.warning('请填写完整注册信息（姓名选填）')
    return
  }
  loading.value = true
  try {
    const user = await authApi.register({
      username: form.username,
      password: form.password,
      realName: form.realName,
      email: form.email,
      classCode: form.classCode,
      emailCode: form.emailCode,
      captchaId: captcha.value?.captchaId ?? '',
      captchaCode: form.captchaCode
    })
    ElMessage.success(`注册成功，欢迎 ${user.realName || user.username}，请登录`)
    router.push('/login')
  } finally {
    loading.value = false
  }
}

onMounted(refreshCaptcha)
onUnmounted(() => {
  if (timer !== undefined) window.clearInterval(timer)
})
</script>

<style scoped>
.register-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--aies-paper);
  padding: 24px;
}
.register-card {
  width: 400px;
  max-width: 100%;
  border-radius: var(--aies-radius-lg);
  box-shadow: var(--aies-shadow);
  border: 1px solid var(--aies-border);
}
.title {
  text-align: center;
  font-size: 18px;
  font-weight: 600;
}
.code-row,
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
  margin-top: 12px;
  text-align: center;
  font-size: 13px;
}
.links a {
  color: var(--el-color-primary);
  text-decoration: none;
}
</style>
