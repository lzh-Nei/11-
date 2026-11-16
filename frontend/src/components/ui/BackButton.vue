<template>
  <el-button size="small" plain @click="goBack">返回上一页</el-button>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

/** 新窗口下优先关闭回到来源页；同窗口下走浏览器历史；无历史则回模块首页。 */
function goBack() {
  if (window.opener && window.history.length <= 1) {
    window.close()
    return
  }
  if (window.history.length > 1) {
    router.back()
    return
  }
  router.push('/modules')
}
</script>
