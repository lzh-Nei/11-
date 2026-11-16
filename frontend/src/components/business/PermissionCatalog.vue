<template>
  <!-- 统一权限清单组件（WP8-C4）：管理员侧显示在"功能模块 → 用户与权限 → 权限清单"，
       教师/学生侧保留在个人中心；点击权限点按角色分流到对应模块 -->
  <section class="catalog">
    <div class="catalog-head">
      <h3>功能权限（{{ auth.me?.permissions?.length ?? 0 }} 项）</h3>
      <span class="dim">点击权限点直达对应模块；虚线为 M3 预留</span>
    </div>
    <ScrollArea :max-height="maxHeight" padded>
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
    </ScrollArea>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import ScrollArea from '@/components/ui/ScrollArea.vue'
import { permLabel, nsLabel, permEntry } from '@/constants/permissions'
import { toast } from '@/utils/toast'
import { openWindow } from '@/utils/window'

withDefaults(defineProps<{ maxHeight?: string }>(), { maxHeight: '360px' })

const router = useRouter()
const auth = useAuthStore()

const permGroups = computed<Record<string, string[]>>(() => {
  const groups: Record<string, string[]> = {}
  for (const p of auth.me?.permissions ?? []) {
    const ns = String(p).split(':')[0]
    ;(groups[ns] = groups[ns] || []).push(String(p))
  }
  return groups
})

function permTooltip(code: string): string {
  const entry = permEntry(code)
  if (!entry) return String(code)
  return `${entry.hint}（${code}）`
}

async function onLogout() {
  await auth.logout()
  router.push('/login')
}

/** 权限即导航：角色相关入口统一落到功能模块的二级/三级深链 */
function onPermClick(code: string) {
  const isStudent = auth.role === 'STUDENT'
  const isAdmin = auth.role === 'ADMIN'
  const toModule = (group: string, child?: string) =>
    `/modules?group=${group}${child ? `&child=${child}` : ''}`
  const studyModule = (child: string) => toModule('study', child)
  const teachingModule = (child: string) => toModule('teaching', child)

  // 真正未开放的权限（task:edit / report:export）优先如实告知，不做导航
  const entry = permEntry(code)
  if (entry?.kind === 'reserved') {
    toast.warning(`预留权限：${entry.hint}`)
    return
  }

  if (code === 'task:create' || code === 'task:publish' || code === 'task:view' || code === 'task:mine') {
    router.push(isStudent ? studyModule('tasks') : teachingModule('tasks'))
    return
  }
  if (code === 'submission:view' || code === 'submission:mine' || code === 'submission:upload') {
    router.push(isStudent ? studyModule('submissions') : teachingModule('review'))
    return
  }
  if (code === 'evaluation:view') {
    router.push(isStudent ? studyModule('evaluations') : teachingModule('review'))
    return
  }
  if (
    code === 'evaluation:review' ||
    code === 'evaluation:re-evaluate' ||
    code === 'evaluation:history:view'
  ) {
    router.push(teachingModule('review'))
    return
  }
  if (code === 'class:manage') {
    if (isAdmin) router.push(toModule('data', 'classes'))
    else toast.warning('班级管理入口当前仅在管理端开放')
    return
  }
  if (code.startsWith('report:')) {
    if (isStudent) {
      router.push(toModule('overview'))
      return
    }
    if (isAdmin) {
      toast.warning('管理员暂不开放全局聚合报表（C1 范围收敛）')
      return
    }
    router.push(toModule('report'))
    return
  }

  if (!entry) return
  if (entry.kind === 'route') {
    router.push(entry.to)
    return
  }
  switch (entry.act) {
    case 'logout':
      onLogout()
      break
    case 'profile':
      router.push('/account')
      break
    case 'dashboard':
      openWindow('/report.html')
      break
  }
}
</script>

<style scoped>
.catalog {
  background: var(--aies-surface);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  box-shadow: var(--aies-shadow);
  padding: var(--aies-gap-lg);
}
.catalog-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.catalog-head h3 {
  margin: 0;
  font-size: 15px;
  color: var(--aies-ink);
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
