<template>
  <!-- 注册页（A-08 / UC-43）：AuthCard 壳 + CaptchaInput + EmailCodeInput 组件化 -->
  <AuthCard title="AIES 注册账号">
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
        <EmailCodeInput
          v-model:code="form.emailCode"
          scene="REGISTER"
          :username="form.username"
          :email="form.email"
          :captcha-id="captcha?.captchaId ?? ''"
          :captcha-code="form.captchaCode"
          @sent="refreshCaptcha"
          @submit="onSubmit"
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
      <el-button type="primary" class="submit-btn" :loading="loading" @click="onSubmit">注 册</el-button>
      <div class="links">
        <router-link to="/login">已有账号？返回登录</router-link>
      </div>
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

async function refreshCaptcha() {
  captcha.value = await authApi.captcha()
  form.captchaCode = ''
}

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
</script>

<style scoped>
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
