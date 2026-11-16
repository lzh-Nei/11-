<template>
  <!-- 邮箱验证码行（A-06）：输入 + 发送按钮，60s 防重发 + 全局频控冷却（S-12）
       发送成功回抛 sent（父级刷新图形码）；SMTP 不可用（40004）回抛 smtp-down（父级降级提示） -->
  <div class="email-code-row">
    <el-input
      :model-value="code"
      placeholder="6位数字"
      @update:model-value="(v: string) => emit('update:code', v)"
      @keyup.enter="emit('submit')"
    />
    <el-button :loading="sending" :disabled="sendDisabled" @click="onSend">
      {{ sendButtonText }}
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { authApi, type EmailCodeScene } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { storeToRefs } from 'pinia'

const props = withDefaults(
  defineProps<{
    code: string
    scene: EmailCodeScene
    username: string
    email: string
    captchaId: string
    captchaCode: string
    sendLabel?: string
  }>(),
  { sendLabel: '发送邮箱验证码' }
)

const emit = defineEmits<{
  'update:code': [v: string]
  sent: []
  'smtp-down': []
  submit: []
}>()

const auth = useAuthStore()
const { cooldownSeconds } = storeToRefs(auth)

const sending = ref(false)
const countdown = ref(0)
let timer: number | undefined

async function onSend() {
  if (!props.username || !props.email || !props.captchaId || !props.captchaCode) {
    ElMessage.warning('请先填写学号、邮箱与图形验证码')
    return
  }
  sending.value = true
  try {
    await authApi.emailCode({
      username: props.username,
      email: props.email,
      scene: props.scene,
      captchaId: props.captchaId,
      captchaCode: props.captchaCode
    })
    // 未配置邮件服务时后端仍会生成验证码；页面统一按已发送提示，不暴露模拟细节
    ElMessage.success('验证码已发送，请查收邮箱')
    startCountdown()
    emit('sent')
  } catch (e) {
    // 拦截器已提示后端message；此处按 UC-44 B计划口径补抛 SMTP 降级事件
    if ((e as { code?: number })?.code === 40004) {
      emit('smtp-down')
    }
  } finally {
    sending.value = false
  }
}

/** 60s 防重发倒计时（后端单邮箱冷却 60s，S-12 频控） */
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
  return props.sendLabel
})

onUnmounted(() => {
  if (timer !== undefined) window.clearInterval(timer)
})

// 暴露 send：父级在图形码输入框回车时同样可触发重发（找回密码步骤2口径）
defineExpose({ send: onSend })
</script>

<style scoped>
.email-code-row {
  display: flex;
  gap: 8px;
  width: 100%;
}
</style>
