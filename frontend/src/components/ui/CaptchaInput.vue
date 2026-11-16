<template>
  <!-- 图形验证码输入：A-04 契约（captchaId+image），失败/点击自动换新码 -->
  <div class="captcha-input">
    <el-input
      :model-value="code"
      placeholder="图形验证码，区分大小写"
      maxlength="8"
      autocomplete="off"
      @update:model-value="(v: string) => emit('update:code', v)"
      @keyup.enter="emit('submit')"
    />
    <img
      v-if="image"
      :src="image"
      class="captcha-img"
      title="看不清？点击换一张"
      alt="图形验证码"
      @click="emit('refresh')"
    />
    <span v-else class="captcha-loading" @click="emit('refresh')">获取中…</span>
  </div>
</template>

<script setup lang="ts">
defineProps<{ code: string; image: string | null }>()
const emit = defineEmits<{ 'update:code': [v: string]; refresh: []; submit: [] }>()
</script>

<style scoped>
.captcha-input {
  display: flex;
  gap: 8px;
  width: 100%;
}
.captcha-img {
  height: 32px;
  flex: none;
  cursor: pointer;
  border-radius: var(--aies-radius);
  border: 1px solid var(--aies-border);
}
.captcha-loading {
  flex: none;
  font-size: 12px;
  color: var(--aies-ink3);
  line-height: 32px;
  cursor: pointer;
}
</style>
