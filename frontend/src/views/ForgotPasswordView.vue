<template>
  <div class="forgot-wrap">
    <el-card class="forgot-card">
      <template #header>
        <div class="title">AIES 找回密码</div>
      </template>
      <el-steps :active="step - 1" simple finish-status="success" class="steps">
        <el-step title="身份校验" />
        <el-step title="重置密码" />
      </el-steps>

      <!-- 步骤1：学号+邮箱+图形码（A-09，S-13防枚举统一文案） -->
      <el-form v-if="step === 1" label-position="top" @submit.prevent="onCheckIdentity">
        <el-form-item label="学号">
          <el-input v-model="form.username" placeholder="请输入学号" autocomplete="username" />
        </el-form-item>
        <el-form-item label="绑定邮箱">
          <el-input v-model="form.email" placeholder="注册时绑定的邮箱" />
        </el-form-item>
        <el-form-item label="图形验证码">
          <div class="captcha-row">
            <el-input v-model="form.captchaCode" placeholder="区分大小写" @keyup.enter="onCheckIdentity" />
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
        <el-button type="primary" class="submit-btn" :loading="loading" @click="onCheckIdentity">
          下一步
        </el-button>
        <div class="links">
          <router-link to="/login">返回登录</router-link>
        </div>
      </el-form>

      <!-- 步骤2：重发重置码（需新图形码）+ 邮箱码 + 新密码（A-10） -->
      <el-form v-else label-position="top" @submit.prevent="onReset">
        <el-alert
          type="success"
          show-icon
          :closable="false"
          title="如该账号存在，重置邮件已发送"
          class="hint"
        />
        <!-- UC-44 B计划口径：40004=SMTP不可用降级提示 -->
        <el-alert
          v-if="smtpDown"
          type="warning"
          show-icon
          :closable="false"
          title="邮件服务暂不可用，演示请使用账号密码登录"
          class="hint"
        />
        <el-form-item label="图形验证码">
          <div class="captcha-row">
            <el-input v-model="form.captchaCode" placeholder="未收到邮件可重发，需先填图形码" @keyup.enter="onSendCode" />
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
        <el-form-item label="邮箱验证码">
          <div class="code-row">
            <el-input v-model="form.emailCode" placeholder="6位数字" />
            <el-button :loading="sending" :disabled="sendDisabled" @click="onSendCode">
              {{ sendButtonText }}
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="新密码">
          <el-input
            v-model="form.newPassword"
            type="password"
            placeholder="至少8位，同时含字母和数字"
            show-password
            autocomplete="new-password"
            @keyup.enter="onReset"
          />
        </el-form-item>
        <el-button type="primary" class="submit-btn" :loading="loading" @click="onReset">
          重置密码
        </el-button>
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

const step = ref(1)
const form = reactive({
  username: '',
  email: '',
  captchaCode: '',
  emailCode: '',
  newPassword: ''
})
const captcha = ref<{ captchaId: string; image: string } | null>(null)
const loading = ref(false)
const sending = ref(false)
const countdown = ref(0)
const smtpDown = ref(false)

let timer: number | undefined

async function refreshCaptcha() {
  captcha.value = await authApi.captcha()
  form.captchaCode = ''
}

/** 步骤1 身份校验（A-09）：后端无论命中与否code=0，前端按S-13统一文案提示，不得分叉 */
async function onCheckIdentity() {
  if (!form.username || !form.email || !form.captchaCode) {
    ElMessage.warning('请填写学号、邮箱与图形验证码')
    return
  }
  loading.value = true
  try {
    await authApi.forgotPassword({
      username: form.username,
      email: form.email,
      captchaId: captcha.value?.captchaId ?? '',
      captchaCode: form.captchaCode
    })
    // S-13 防枚举统一文案：无论账号是否存在同形提示
    ElMessage.success('如该账号存在，重置邮件已发送')
    step.value = 2
    // 步骤1图形码已被消费，进入步骤2装载新码供重发按钮使用
    await refreshCaptcha()
  } finally {
    loading.value = false
  }
}

/** 步骤2 重发重置码（A-06 scene=RESET_PASSWORD）；40004=SMTP不可用→B计划降级提示 */
async function onSendCode() {
  if (!form.captchaCode) {
    ElMessage.warning('请先填写图形验证码')
    return
  }
  sending.value = true
  try {
    await authApi.emailCode({
      username: form.username,
      email: form.email,
      scene: 'RESET_PASSWORD',
      captchaId: captcha.value?.captchaId ?? '',
      captchaCode: form.captchaCode
    })
    ElMessage.success('发送成功，请查收邮箱')
    startCountdown()
    await refreshCaptcha()
  } catch (e) {
    // 拦截器已提示后端message；此处按UC-44 B计划口径补充持久降级提示
    if ((e as { code?: number })?.code === 40004) {
      smtpDown.value = true
    }
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
  return '发送重置码'
})

/** 提交重置（A-10）：弱密码→30013、码无效→30009由拦截器统一提示；成功即回登录页 */
async function onReset() {
  if (!form.emailCode || !form.newPassword) {
    ElMessage.warning('请填写邮箱验证码与新密码')
    return
  }
  loading.value = true
  try {
    await authApi.resetPassword({
      username: form.username,
      emailCode: form.emailCode,
      newPassword: form.newPassword
    })
    ElMessage.success('密码重置成功，请使用新密码登录')
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
.forgot-wrap {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f2d3d 0%, #2b4a6f 100%);
}
.forgot-card {
  width: 380px;
}
.title {
  text-align: center;
  font-size: 18px;
  font-weight: 600;
}
.steps {
  margin-bottom: 16px;
}
.hint {
  margin-bottom: 12px;
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
