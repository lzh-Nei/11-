<template>
  <div class="home">
    <el-header class="header">
      <div class="brand">AIES 教学AI检查评价系统</div>
      <div class="user-zone">
        <span class="hello">{{ auth.me?.realName }}（{{ auth.me?.username }}）</span>
        <el-tag :type="roleTagType" effect="dark">{{ auth.role }}</el-tag>
        <el-button size="small" @click="onLogout">登出</el-button>
      </div>
    </el-header>

    <el-main class="main">
      <el-alert
        v-if="auth.firstLogin"
        title="首次登录：请尽快修改初始密码（UC-07，改密引导开发中）"
        type="warning"
        show-icon
        :closable="false"
        class="first-login"
      />
      <el-card>
        <template #header>当前用户信息（GET /api/auth/me 装载）</template>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="学号/工号">{{ auth.me?.username }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ auth.me?.realName }}</el-descriptions-item>
          <el-descriptions-item label="角色">
            <el-tag v-for="r in auth.me?.roles ?? []" :key="r" class="tag-gap">{{ r }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ auth.me?.email || '—' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card class="nav-card" v-if="auth.role === 'STUDENT'">
        <template #header>学生工作台（M1 链路）</template>
        <div class="nav-list">
          <el-button type="primary" @click="router.push('/tasks')">我的任务</el-button>
          <el-button type="success" @click="router.push('/submissions')">我的提交记录</el-button>
        </div>
      </el-card>

      <el-card class="perm-card">
        <template #header>权限清单（{{ auth.me?.permissions?.length ?? 0 }} 项）</template>
        <div class="perm-list">
          <el-tag
            v-for="p in auth.me?.permissions ?? []"
            :key="p"
            type="info"
            effect="plain"
            class="tag-gap"
          >
            {{ p }}
          </el-tag>
        </div>
      </el-card>
    </el-main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const roleTagType = computed(() => {
  if (auth.role === 'ADMIN') return 'danger'
  if (auth.role === 'TEACHER') return 'warning'
  return 'success'
})

async function onLogout() {
  await auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.home {
  min-height: 100vh;
  background: #f5f7fa;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}
.brand {
  font-size: 17px;
  font-weight: 600;
}
.user-zone {
  display: flex;
  align-items: center;
  gap: 12px;
}
.main {
  max-width: 960px;
  margin: 0 auto;
}
.first-login {
  margin-bottom: 16px;
}
.perm-card {
  margin-top: 16px;
}
.nav-card {
  margin-top: 16px;
}
.nav-list {
  display: flex;
  gap: 12px;
}
.perm-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.tag-gap {
  margin-right: 4px;
}
</style>
