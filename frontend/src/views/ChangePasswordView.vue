<template>
  <!-- 登录后自助改密（UC-07）：复用邮箱验证码通道（A-06 scene=RESET_PASSWORD → A-10 重置）
       成功即作废该用户全部 RT（S-10），前端同步清态回登录页 -->
  <PageShell :width="860">
    <PageHeader title="修改密码" subtitle="账号安全 · 邮箱验证码校验（A-06 / A-10，成功需重新登录）">
      <el-button @click="router.push('/')">返回首页</el-button>
    </PageHeader>

    <el-alert
      v-if="!emailBound"
      type="warning"
      show-icon
      :closable="false"
      title="当前账号未绑定邮箱，无法使用自助改密；请联系授课教师或管理员处理。"
      class="tip"
    />

    <section class="panel">
      <el-descriptions :column="2" border class="who">
        <el-descriptions-item label="学号/工号">{{ me?.username ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ me?.realName ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="绑定邮箱" :span="2">{{ me?.email ?? '—' }}</el-descriptions-item>
      </el-descriptions>

      <el-form label-position="top" class="form" @submit.prevent="onSubmit">
        <el-form-item label="图形验证码" required>
          <CaptchaInput
            v-model:code="form.captchaCode"
            :image="captcha?.image ?? null"
            @refresh="refreshCaptcha"
            @submit="onSendCode"
          />
        </el-form-item>
        <el-form-item label="邮箱验证码" required>
          <EmailCodeInput
            ref="codeInput"
            v-model:code="form.emailCode"
            scene="RESET_PASSWORD"
            :username="me?.username ?? ''"
            :email="me?.email ?? ''"
            :captcha-id="captcha?.captchaId ?? ''"
            :captcha-code="form.captchaCode"
            send-label="发送改密验证码"
            @sent="refreshCaptcha"
            @smtp-down="smtpDown = true"
          />
        </el-form-item>
        <el-alert
          v-if="smtpDown"
          type="warning"
          show-icon
          :closable="false"
          title="邮件服务暂不可用，暂时无法自助改密；请联系管理员重置。"
          class="tip"
        />
        <el-form-item label="新密码" required>
          <el-input
            v-model="form.newPassword"
            type="password"
            placeholder="至少8位，同时含字母和数字"
            show-password
            autocomplete="new-password"
          />
        </el-form-item>
        <el-form-item label="确认新密码" required>
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="再次输入新密码"
            show-password
            autocomplete="new-password"
            @keyup.enter="onSubmit"
          />
        </el-form-item>
        <p v-if="hint" class="hint">{{ hint }}</p>
        <div class="actions">
          <el-button type="primary" :loading="loading" :disabled="!canSubmit" @click="onSubmit">
            重置密码并重新登录
          </el-button>
        </div>
      </el-form>
    </section>
  </PageShell>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { toast } from '@/utils/toast'
import PageShell from '@/components/ui/PageShell.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import CaptchaInput from '@/components/ui/CaptchaInput.vue'
import EmailCodeInput from '@/components/business/EmailCodeInput.vue'

const router = useRouter()
const auth = useAuthStore()
const me = computed(() => auth.me)

const captcha = ref<{ captchaId: string; image: string } | null>(null)
const loading = ref(false)
const smtpDown = ref(false)
const codeInput = ref<{ send: () => Promise<void> } | null>(null)

const form = reactive({
  captchaCode: '',
  emailCode: '',
  newPassword: '',
  confirmPassword: ''
})

const emailBound = computed(() => Boolean(me.value?.email))
const passwordStrong = computed(
  () => form.newPassword.length >= 8 && /[A-Za-z]/.test(form.newPassword) && /\d/.test(form.newPassword)
)
const hint = computed(() => {
  if (form.newPassword && !passwordStrong.value) return '密码强度不足：至少8位，且同时包含字母和数字'
  if (form.confirmPassword && form.newPassword !== form.confirmPassword) return '两次输入的新密码不一致'
  return ''
})
const canSubmit = computed(
  () =>
    emailBound.value &&
    passwordStrong.value &&
    form.newPassword === form.confirmPassword &&
    form.emailCode.length > 0
)

async function refreshCaptcha() {
  captcha.value = await authApi.captcha()
  form.captchaCode = ''
}

/** 图形码回车 → 直接触发发送（与找回密码页同口径） */
function onSendCode() {
  codeInput.value?.send()
}

async function onSubmit() {
  if (!canSubmit.value || !me.value) return
  loading.value = true
  try {
    await authApi.resetPassword({
      username: me.value.username,
      emailCode: form.emailCode,
      newPassword: form.newPassword
    })
    toast.success('密码已重置，请使用新密码重新登录')
    auth.clear()
    router.push('/login')
  } catch {
    // 失败由拦截器统一提示（30009 码无效 / 30013 弱密码）；邮箱码一次性，重发需新图形码
    await refreshCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(refreshCaptcha)
</script>

<style scoped>
.tip {
  margin-bottom: var(--aies-gap);
}
.panel {
  background: var(--aies-surface);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  box-shadow: var(--aies-shadow);
  padding: var(--aies-gap-lg);
}
.who {
  margin-bottom: var(--aies-gap-lg);
}
.form {
  max-width: 420px;
}
.hint {
  margin: 0 0 12px;
  font-size: 12.5px;
  color: var(--aies-brand);
}
.actions {
  margin-top: 4px;
}
</style>
