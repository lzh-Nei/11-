<template>
  <!-- 找回密码（A-09/A-10 / UC-44）：AuthCard 壳 + CaptchaInput + EmailCodeInput 组件化 -->
  <AuthCard title="AIES 找回密码">
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
        <CaptchaInput
          v-model:code="form.captchaCode"
          :image="captcha?.image ?? null"
          @refresh="refreshCaptcha"
          @submit="onCheckIdentity"
        />
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
        title="邮件服务暂不可用，请暂时使用账号密码登录"
        class="hint"
      />
      <el-form-item label="图形验证码">
        <CaptchaInput
          v-model:code="form.captchaCode"
          :image="captcha?.image ?? null"
          @refresh="refreshCaptcha"
          @submit="onSendCode"
        />
      </el-form-item>
      <el-form-item label="邮箱验证码">
        <EmailCodeInput
          ref="codeInput"
          v-model:code="form.emailCode"
          scene="RESET_PASSWORD"
          :username="form.username"
          :email="form.email"
          :captcha-id="captcha?.captchaId ?? ''"
          :captcha-code="form.captchaCode"
          send-label="发送重置码"
          @sent="refreshCaptcha"
          @smtp-down="smtpDown = true"
        />
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
      <el-button type="primary" class="submit-btn" :loading="loading" @click="onReset"> 重置密码 </el-button>
    </el-form>
  </AuthCard>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api/auth'
import AuthCard from '@/components/ui/AuthCard.vue'
import CaptchaInput from '@/components/ui/CaptchaInput.vue'
import EmailCodeInput from '@/components/business/EmailCodeInput.vue'

const router = useRouter()

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
const smtpDown = ref(false)

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

/** 步骤2 重发重置码（A-06 scene=RESET_PASSWORD）：发送逻辑收口在 EmailCodeInput，回车兜底转调其 send */
function onSendCode() {
  codeInput.value?.send()
}

const codeInput = ref<InstanceType<typeof EmailCodeInput> | null>(null)

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
</script>

<style scoped>
.steps {
  margin-bottom: 16px;
}
.hint {
  margin-bottom: 12px;
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
