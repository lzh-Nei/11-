<template>
  <!-- 个人中心：账号档案 + 安全操作；权限清单统一收口到功能模块（管理员）/
       保留在个人中心（教师、学生） -->
  <PageShell>
    <PageHeader title="个人中心" subtitle="账号档案与安全操作（GET /api/auth/me）">
      <el-button size="small" :loading="reloading" @click="reloadMe">刷新用户信息</el-button>
    </PageHeader>

    <div class="top-grid">
      <section class="card">
        <h3>账号档案</h3>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="学号/工号">{{ auth.me?.username ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ auth.me?.realName ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="角色">
            <el-tag
              v-for="r in auth.me?.roles ?? []"
              :key="r"
              class="tag-gap"
              effect="dark"
              :type="roleTagType"
              :title="r"
            >
              {{ roleName(r) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ auth.me?.email || '未绑定' }}</el-descriptions-item>
          <el-descriptions-item v-if="auth.me?.phone" label="手机号" :span="2">
            {{ maskPhone(auth.me.phone) }}
          </el-descriptions-item>
        </el-descriptions>
      </section>

      <section class="card">
        <h3>安全操作</h3>
        <div class="security-list">
          <div class="security-item">
            <div>
              <b>修改密码</b>
              <small>邮箱验证码校验；成功后需重新登录（作废全部刷新令牌）</small>
            </div>
            <el-button size="small" @click="router.push('/change-password')">去修改</el-button>
          </div>
          <div class="security-item">
            <div>
              <b>退出登录</b>
              <small>当前访问令牌立即加入黑名单</small>
            </div>
            <el-button size="small" plain @click="onLogout">退出</el-button>
          </div>
        </div>
        <p v-if="auth.firstLogin" class="first-tip">首次登录：建议先修改初始密码（UC-07）。</p>
      </section>
    </div>

    <!-- 教师/学生保留权限清单；管理员统一在「功能模块 → 用户与权限 → 权限清单」 -->
    <PermissionCatalog v-if="!isAdmin" class="perm-block" />
  </PageShell>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import PageShell from '@/components/ui/PageShell.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import PermissionCatalog from '@/components/business/PermissionCatalog.vue'
import { toast } from '@/utils/toast'

const router = useRouter()
const auth = useAuthStore()
const reloading = ref(false)

const isAdmin = computed(() => auth.role === 'ADMIN')
const roleTagType = computed(() => {
  if (auth.role === 'ADMIN') return 'danger'
  if (auth.role === 'TEACHER') return 'warning'
  return 'success'
})

function roleName(code: string): string {
  if (code === 'ADMIN') return '管理员'
  if (code === 'TEACHER') return '教师'
  if (code === 'STUDENT') return '学生'
  return code
}

function maskPhone(phone: string): string {
  if (!phone || phone.length < 7) return '—'
  return `${phone.slice(0, 3)}****${phone.slice(-4)}`
}

async function reloadMe() {
  reloading.value = true
  try {
    await auth.loadMe()
    toast.success('用户信息已刷新')
  } catch {
    toast.error('用户信息刷新失败', '请稍后重试')
  } finally {
    reloading.value = false
  }
}

async function onLogout() {
  await auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.top-grid {
  display: grid;
  grid-template-columns: 1.15fr 0.85fr;
  gap: var(--aies-gap);
}
@media (max-width: 1024px) {
  .top-grid {
    grid-template-columns: 1fr;
  }
}
.card {
  background: var(--aies-surface);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  box-shadow: var(--aies-shadow);
  padding: var(--aies-gap-lg);
}
.card h3 {
  margin: 0 0 12px;
  font-size: 15px;
  color: var(--aies-ink);
}
.tag-gap {
  margin-right: 6px;
}
.security-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.security-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius);
  background: var(--aies-paper);
}
.security-item b {
  display: block;
  font-size: 13.5px;
  color: var(--aies-ink);
}
.security-item small {
  display: block;
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--aies-ink3);
  line-height: 1.5;
}
.first-tip {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--aies-brand);
}
.perm-block {
  margin-top: var(--aies-gap);
}
</style>
