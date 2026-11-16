<template>
  <!-- 个人中心（模块）：账号档案 + 安全操作 + 权限清单（权限点来自 /me，可点即导航） -->
  <PageShell>
    <PageHeader title="个人中心" subtitle="账号档案、安全操作与功能权限（GET /api/auth/me）">
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

    <section class="card perm-card">
      <div class="perm-head">
        <h3>功能权限（{{ auth.me?.permissions?.length ?? 0 }} 项）</h3>
        <span class="dim">点击权限点直达对应模块；虚线为 M3 预留</span>
      </div>
      <div v-for="(perms, ns) in permGroups" :key="ns" class="perm-group">
        <div class="ns-title">{{ nsLabel(ns) }} · {{ perms.length }}</div>
        <div class="perm-list">
          <el-tooltip v-for="p in perms" :key="p" :content="permTooltip(p)" placement="top">
            <button
              type="button"
              class="perm-chip"
              :class="{
                reserved: permEntry(p)?.kind === 'reserved',
                action: permEntry(p)?.kind === 'action'
              }"
              @click="onPermClick(p)"
            >
              {{ permLabel(p) }}
              <i v-if="permEntry(p)?.kind === 'route'" class="chip-arrow">→</i>
              <i v-else-if="permEntry(p)?.kind === 'reserved'" class="chip-lock">预留</i>
            </button>
          </el-tooltip>
        </div>
      </div>
    </section>
  </PageShell>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import PageShell from '@/components/ui/PageShell.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import { permLabel, nsLabel, permEntry } from '@/constants/permissions'
import { toast } from '@/utils/toast'
import { openWindow } from '@/utils/window'

const router = useRouter()
const auth = useAuthStore()
const reloading = ref(false)

const roleTagType = computed(() => {
  if (auth.role === 'ADMIN') return 'danger'
  if (auth.role === 'TEACHER') return 'warning'
  return 'success'
})

/* 权限清单：中文化 + 命名空间分组（禁裸英文权限点） */
const permGroups = computed<Record<string, string[]>>(() => {
  const groups: Record<string, string[]> = {}
  for (const p of auth.me?.permissions ?? []) {
    const ns = String(p).split(':')[0]
    ;(groups[ns] = groups[ns] || []).push(String(p))
  }
  return groups
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

function permTooltip(code: string): string {
  const entry = permEntry(code)
  if (!entry) return String(code)
  return `${entry.hint}（${code}）`
}

/** 权限即导航：角色相关的入口在常量映射之上做少量覆盖，避免进入无权模块 */
function onPermClick(code: string) {
  const isStudent = auth.role === 'STUDENT'

  if (code === 'task:view' || code === 'task:mine') {
    router.push(isStudent ? '/tasks' : '/tasks/manage')
    return
  }
  if (code === 'submission:view' || code === 'submission:mine' || code === 'submission:upload') {
    router.push(isStudent ? '/submissions' : '/review')
    return
  }
  if (code === 'evaluation:view') {
    router.push(isStudent ? '/my-evaluations' : '/review')
    return
  }
  if (
    code === 'evaluation:review' ||
    code === 'evaluation:re-evaluate' ||
    code === 'evaluation:history:view'
  ) {
    router.push('/review')
    return
  }
  if (code.startsWith('report:')) {
    if (isStudent) {
      router.push('/')
      return
    }
    // C1 裁决：管理员暂不开放全局聚合，管理员点击报表权限给出明确说明而非打开 403 页面
    if (!(auth.me?.permissions ?? []).includes('report:view:teacher')) {
      toast.warning('管理员暂不开放全局聚合报表（C1 范围收敛）')
      return
    }
    openWindow('/report.html')
    return
  }

  const entry = permEntry(code)
  if (!entry) return
  if (entry.kind === 'route') {
    router.push(entry.to)
    return
  }
  if (entry.kind === 'reserved') {
    toast.warning(`预留权限：${entry.hint}`)
    return
  }
  switch (entry.act) {
    case 'logout':
      onLogout()
      break
    case 'profile':
      document.querySelector('.card')?.scrollIntoView({ behavior: 'smooth' })
      break
    case 'dashboard':
      openWindow('/report.html')
      break
  }
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
.perm-card {
  margin-top: var(--aies-gap);
}
.perm-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.dim {
  font-size: 12px;
  color: var(--aies-ink3);
}
.perm-group + .perm-group {
  margin-top: 14px;
}
.ns-title {
  font-size: 11.5px;
  color: var(--aies-ink3);
  letter-spacing: 0.06em;
  margin-bottom: 6px;
}
.perm-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.perm-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 11px;
  border-radius: var(--aies-radius-pill);
  border: 1px solid var(--aies-border);
  background: var(--aies-surface);
  color: var(--aies-ink2);
  font-size: 12.5px;
  font-family: inherit;
  cursor: pointer;
  transition:
    box-shadow var(--aies-t),
    transform var(--aies-t),
    border-color var(--aies-t),
    color var(--aies-t);
}
.perm-chip:hover {
  border-color: var(--aies-brand);
  color: var(--aies-brand);
  box-shadow: var(--aies-shadow-hover);
  transform: var(--aies-lift);
}
.perm-chip.action {
  border-style: solid;
}
.perm-chip.reserved {
  border-style: dashed;
  color: var(--aies-ink3);
  cursor: help;
}
.perm-chip.reserved:hover {
  border-color: var(--aies-ink3);
  color: var(--aies-ink3);
  box-shadow: none;
  transform: none;
}
.chip-arrow {
  font-style: normal;
  font-size: 11px;
  color: var(--aies-brand);
}
.chip-lock {
  font-style: normal;
  font-size: 10px;
  color: var(--aies-ink3);
  border: 1px dashed var(--aies-border);
  border-radius: 999px;
  padding: 0 5px;
}
</style>
