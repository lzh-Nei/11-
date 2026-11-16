<template>
  <!-- 功能模块（三角色统一）：顶部横向二级模块 + 紧邻顶部栏的纵向三级目录 + 内容展示区。
       权限清单统一收口在「账号与权限 → 权限清单」；内容区弹性填满，避免底部大块留白。 -->
  <PageShell fill fluid>
    <PageHeader
      title="功能模块"
      :subtitle="`${roleTitle} · 二级模块（顶部横向） / 三级目录（左侧纵向） / 功能页面`"
    />

    <div class="browser">
      <nav class="l2-bar" aria-label="二级模块">
        <button
          v-for="g in groups"
          :key="g.key"
          type="button"
          class="l2-item"
          :class="{ active: g.key === activeGroupKey }"
          @click="selectGroup(g)"
        >
          <NavIcon :name="g.icon" />
          <span>{{ g.label }}</span>
          <i v-if="g.external" class="ext">↗</i>
        </button>
      </nav>

      <div class="browser-body" :class="{ 'no-l3': !hasChildren }">
        <aside v-if="hasChildren" class="l3-nav" aria-label="三级目录">
          <p class="l3-title">{{ selectedGroup?.label }}</p>
          <button
            v-for="c in selectedGroup?.children ?? []"
            :key="c.key"
            type="button"
            class="l3-item"
            :class="{ active: c.key === activeChildKey }"
            @click="selectChild(c)"
          >
            <NavIcon :name="c.icon" />
            <span>{{ c.label }}</span>
            <i v-if="c.external" class="ext">↗</i>
          </button>
        </aside>

        <section class="content">
          <ScrollArea fill padded>
            <div v-if="pageExternal" class="launch">
              <NavIcon :name="selectedGroup?.icon ?? 'chart'" class="launch-icon" />
              <h3>{{ selectedChild?.label ?? selectedGroup?.label }}</h3>
              <p>该模块以独立窗口展示（报表驾驶舱为独立静态页面）。</p>
              <el-button type="primary" @click="openWindow(pageExternal)">在新窗口打开 ↗</el-button>
            </div>
            <component :is="pageComponent" v-else-if="pageComponent" v-bind="pageProps ?? {}" />
            <div v-else class="placeholder">请选择左侧三级目录</div>
          </ScrollArea>
        </section>
      </div>
    </div>
  </PageShell>
</template>

<script setup lang="ts">
import { computed, ref, watch, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import PageShell from '@/components/ui/PageShell.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import ScrollArea from '@/components/ui/ScrollArea.vue'
import NavIcon from '@/components/ui/NavIcon.vue'
import PermissionCatalog from '@/components/business/PermissionCatalog.vue'
import DashboardView from '@/views/DashboardView.vue'
import StudentTasks from '@/views/StudentTasks.vue'
import StudentSubmissions from '@/views/StudentSubmissions.vue'
import MyEvaluationsView from '@/views/MyEvaluationsView.vue'
import Check from '@/views/Check.vue'
import ReviewHubView from '@/views/ReviewHubView.vue'
import UsersPanel from '@/components/admin/UsersPanel.vue'
import RolesPanel from '@/components/admin/RolesPanel.vue'
import ClassesPanel from '@/components/admin/ClassesPanel.vue'
import AuditLogsPanel from '@/components/admin/AuditLogsPanel.vue'
import LlmConfigPanel from '@/components/admin/LlmConfigPanel.vue'
import PromptPanel from '@/components/admin/PromptPanel.vue'
import GoldenPanel from '@/components/admin/GoldenPanel.vue'
import ConfigPanel from '@/components/admin/ConfigPanel.vue'
import { openWindow } from '@/utils/window'

interface ModuleChild {
  key: string
  label: string
  icon: string
  component?: Component
  props?: Record<string, unknown>
  external?: string
}
interface ModuleGroup {
  key: string
  label: string
  icon: string
  children?: ModuleChild[]
  component?: Component
  props?: Record<string, unknown>
  external?: string
}

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const permCatalog: ModuleChild = {
  key: 'perms',
  label: '权限清单',
  icon: 'lock',
  component: PermissionCatalog
}

const studentGroups: ModuleGroup[] = [
  {
    key: 'study',
    label: '学习任务',
    icon: 'tasks',
    children: [
      { key: 'tasks', label: '任务中心', icon: 'tasks', component: StudentTasks, props: { embedded: true } },
      {
        key: 'submissions',
        label: '提交档案',
        icon: 'archive',
        component: StudentSubmissions,
        props: { embedded: true }
      },
      {
        key: 'evaluations',
        label: '我的评价',
        icon: 'evaluation',
        component: MyEvaluationsView,
        props: { embedded: true }
      }
    ]
  },
  {
    key: 'analysis',
    label: '学习分析',
    icon: 'chart',
    children: [
      {
        key: 'overview',
        label: '学习概览',
        icon: 'dashboard',
        component: DashboardView,
        props: { embedded: true }
      }
    ]
  },
  { key: 'access', label: '账号与权限', icon: 'lock', children: [permCatalog] }
]

const teacherGroups: ModuleGroup[] = [
  {
    key: 'teaching',
    label: '教学任务',
    icon: 'manage',
    children: [
      { key: 'tasks', label: '任务管理', icon: 'manage', component: Check, props: { embedded: true } },
      {
        key: 'review',
        label: '评价复核',
        icon: 'review',
        component: ReviewHubView,
        props: { embedded: true }
      }
    ]
  },
  {
    key: 'analysis',
    label: '教学分析',
    icon: 'chart',
    children: [
      {
        key: 'overview',
        label: '教学概览',
        icon: 'dashboard',
        component: DashboardView,
        props: { embedded: true }
      },
      { key: 'report', label: '报表驾驶舱', icon: 'chart', external: '/report.html' }
    ]
  },
  { key: 'access', label: '账号与权限', icon: 'lock', children: [permCatalog] }
]

const adminGroups: ModuleGroup[] = [
  {
    key: 'access',
    label: '用户与权限',
    icon: 'user',
    children: [
      { key: 'users', label: '用户管理', icon: 'user', component: UsersPanel },
      { key: 'roles', label: '角色权限', icon: 'review', component: RolesPanel },
      permCatalog
    ]
  },
  {
    key: 'teaching',
    label: '教学管理',
    icon: 'manage',
    children: [
      { key: 'tasks', label: '任务管理', icon: 'manage', component: Check, props: { embedded: true } },
      {
        key: 'review',
        label: '评价复核',
        icon: 'review',
        component: ReviewHubView,
        props: { embedded: true }
      }
    ]
  },
  {
    key: 'data',
    label: '数据与资产',
    icon: 'archive',
    children: [
      { key: 'classes', label: '班级管理', icon: 'tasks', component: ClassesPanel },
      { key: 'golden', label: '黄金数据集', icon: 'evaluation', component: GoldenPanel }
    ]
  },
  {
    key: 'ai',
    label: 'AI 配置',
    icon: 'chart',
    children: [
      { key: 'llm', label: 'LLM 参数', icon: 'chart', component: LlmConfigPanel },
      { key: 'prompts', label: 'Prompt 模板', icon: 'manage', component: PromptPanel }
    ]
  },
  {
    key: 'ops',
    label: '系统运维',
    icon: 'system',
    children: [
      { key: 'audit', label: '审计日志', icon: 'archive', component: AuditLogsPanel },
      { key: 'config', label: '系统配置', icon: 'system', component: ConfigPanel }
    ]
  }
]

const groups = computed<ModuleGroup[]>(() => {
  if (auth.role === 'STUDENT') return studentGroups
  if (auth.role === 'TEACHER') return teacherGroups
  return adminGroups
})

const roleTitle = computed(() => {
  if (auth.role === 'ADMIN') return '系统管理员'
  if (auth.role === 'TEACHER') return '教师'
  return '学生'
})

const activeGroupKey = ref('')
const activeChildKey = ref('')

function resolveGroup(key: string): ModuleGroup {
  const list = groups.value
  return list.find((g) => g.key === key) ?? list[0]
}

function resolveChild(group: ModuleGroup, key: string): ModuleChild | null {
  if (!group.children?.length) return null
  return group.children.find((c) => c.key === key) ?? group.children[0]
}

/* 支持权限清单/外部链接带 query 直达：/modules?group=study&child=tasks */
function syncFromQuery() {
  const group = resolveGroup(String(route.query.group ?? ''))
  const child = resolveChild(group, String(route.query.child ?? ''))
  activeGroupKey.value = group.key
  activeChildKey.value = child?.key ?? ''
}

syncFromQuery()
watch(groups, syncFromQuery)
watch(
  () => [route.query.group, route.query.child],
  () => {
    if (route.query.group || route.query.child) syncFromQuery()
  }
)

const selectedGroup = computed(() => groups.value.find((g) => g.key === activeGroupKey.value) ?? null)
const selectedChild = computed(
  () => selectedGroup.value?.children?.find((c) => c.key === activeChildKey.value) ?? null
)
const hasChildren = computed(() => (selectedGroup.value?.children?.length ?? 0) > 0)
const pageComponent = computed(() => selectedChild.value?.component ?? selectedGroup.value?.component ?? null)
const pageProps = computed(() => selectedChild.value?.props ?? selectedGroup.value?.props ?? null)
const pageExternal = computed(() => selectedChild.value?.external ?? selectedGroup.value?.external ?? '')

function writeQuery(groupKey: string, childKey: string) {
  router.replace({ query: childKey ? { group: groupKey, child: childKey } : { group: groupKey } })
}

function selectGroup(group: ModuleGroup) {
  activeGroupKey.value = group.key
  activeChildKey.value = group.children?.[0]?.key ?? ''
  writeQuery(group.key, activeChildKey.value)
  if (group.external) openWindow(group.external)
}

function selectChild(child: ModuleChild) {
  activeChildKey.value = child.key
  writeQuery(activeGroupKey.value, child.key)
  if (child.external) openWindow(child.external)
}
</script>

<style scoped>
.browser {
  margin: 0;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  background: var(--aies-surface);
  box-shadow: var(--aies-shadow);
  overflow: hidden;
}
.l2-bar {
  margin: 0;
  display: flex;
  align-items: stretch;
  gap: 4px;
  padding: 6px 10px 0;
  border-bottom: 1px solid var(--aies-border);
  background: var(--aies-surface);
  overflow-x: auto;
  scrollbar-width: thin;
  flex: none;
}
.l2-item {
  margin: 0;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 11px 16px;
  border: none;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--aies-ink3);
  font-family: inherit;
  font-size: 13.5px;
  white-space: nowrap;
  cursor: pointer;
  transition:
    color var(--aies-t),
    border-color var(--aies-t),
    background var(--aies-t);
}
.l2-item:hover {
  color: var(--aies-brand);
  background: var(--aies-paper);
}
.l2-item.active {
  color: var(--aies-brand);
  border-bottom-color: var(--aies-brand);
  font-weight: 600;
}
.ext {
  font-style: normal;
  font-size: 11px;
  color: var(--aies-ink3);
}
.browser-body {
  margin: 0;
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 178px 1fr;
}
.browser-body.no-l3 {
  grid-template-columns: 1fr;
}
@media (max-width: 960px) {
  .browser-body {
    grid-template-columns: 150px 1fr;
  }
}
.l3-nav {
  margin: 0;
  min-height: 0;
  overflow-y: auto;
  border-right: 1px solid var(--aies-border);
  padding: 12px 8px;
  background: var(--aies-paper);
  scrollbar-width: thin;
}
.l3-title {
  margin: 0 0 8px;
  padding: 0 10px;
  font-size: 11px;
  letter-spacing: 0.1em;
  color: var(--aies-ink3);
}
.l3-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--aies-ink2);
  font-family: inherit;
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  transition:
    background var(--aies-t),
    color var(--aies-t);
}
.l3-item + .l3-item {
  margin-top: 2px;
}
.l3-item:hover {
  background: var(--aies-surface);
  color: var(--aies-ink);
}
.l3-item.active {
  background: var(--aies-brand-soft);
  color: var(--aies-brand);
  font-weight: 600;
}
.content {
  margin: 0;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 12px 14px 10px;
}
.launch {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 60px 20px;
  text-align: center;
}
.launch-icon {
  width: 34px;
  height: 34px;
  color: var(--aies-brand);
}
.launch h3 {
  margin: 0;
  font-size: 17px;
  color: var(--aies-ink);
}
.launch p {
  margin: 0 0 6px;
  font-size: 12.5px;
  color: var(--aies-ink3);
}
.placeholder {
  padding: 40px 0;
  text-align: center;
  font-size: 13px;
  color: var(--aies-ink3);
}
</style>
