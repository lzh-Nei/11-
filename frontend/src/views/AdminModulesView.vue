<template>
  <!-- 功能模块（系统管理员）：顶部横向二级模块 + 紧邻顶部栏的纵向三级目录 + 内容展示区。
       权限清单统一收口在「用户与权限 → 权限清单」三级目录中。 -->
  <PageShell>
    <PageHeader
      title="功能模块"
      subtitle="系统管理员 · 二级模块（顶部横向） / 三级目录（左侧纵向） / 功能页面"
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
        </button>
      </nav>

      <div class="browser-body">
        <aside class="l3-nav" aria-label="三级目录">
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
          </button>
        </aside>

        <section class="content">
          <ScrollArea max-height="calc(100vh - 270px)" padded>
            <div v-if="!selectedChild" class="placeholder">请选择左侧三级目录</div>
            <component
              :is="selectedChild.component"
              v-else-if="selectedChild.component"
              v-bind="selectedChild.props ?? {}"
            />
          </ScrollArea>
        </section>
      </div>
    </div>
  </PageShell>
</template>

<script setup lang="ts">
import { computed, ref, watch, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageShell from '@/components/ui/PageShell.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import ScrollArea from '@/components/ui/ScrollArea.vue'
import NavIcon from '@/components/ui/NavIcon.vue'
import UsersPanel from '@/components/admin/UsersPanel.vue'
import RolesPanel from '@/components/admin/RolesPanel.vue'
import ClassesPanel from '@/components/admin/ClassesPanel.vue'
import AuditLogsPanel from '@/components/admin/AuditLogsPanel.vue'
import LlmConfigPanel from '@/components/admin/LlmConfigPanel.vue'
import PromptPanel from '@/components/admin/PromptPanel.vue'
import GoldenPanel from '@/components/admin/GoldenPanel.vue'
import ConfigPanel from '@/components/admin/ConfigPanel.vue'
import PermissionCatalog from '@/components/business/PermissionCatalog.vue'
import Check from '@/views/Check.vue'
import ReviewHubView from '@/views/ReviewHubView.vue'

interface ModuleChild {
  key: string
  label: string
  icon: string
  component?: Component
  props?: Record<string, unknown>
}
interface ModuleGroup {
  key: string
  label: string
  icon: string
  children: ModuleChild[]
}

/* 二级模块（顶部横向）→ 三级目录（左侧纵向）→ 内容页
   顺序按管理员高频路径排列：权限 → 教学 → 数据 → AI → 运维 */
const groups: ModuleGroup[] = [
  {
    key: 'access',
    label: '用户与权限',
    icon: 'user',
    children: [
      { key: 'users', label: '用户管理', icon: 'user', component: UsersPanel },
      { key: 'roles', label: '角色权限', icon: 'review', component: RolesPanel },
      { key: 'perms', label: '权限清单', icon: 'lock', component: PermissionCatalog }
    ]
  },
  {
    key: 'teaching',
    label: '教学与评价',
    icon: 'manage',
    children: [
      {
        key: 'tasks',
        label: '任务管理',
        icon: 'manage',
        component: Check,
        props: { embedded: true }
      },
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

const activeGroupKey = ref(groups[0].key)
const activeChildKey = ref(groups[0].children[0].key)

const route = useRoute()
const router = useRouter()

function resolveGroup(key: string): ModuleGroup {
  return groups.find((g) => g.key === key) ?? groups[0]
}

function resolveChild(group: ModuleGroup, key: string): ModuleChild {
  return group.children.find((c) => c.key === key) ?? group.children[0]
}

/* 支持权限清单/外部链接带 query 直达：/modules?group=access&child=users */
function syncFromQuery() {
  const group = resolveGroup(String(route.query.group ?? ''))
  const child = resolveChild(group, String(route.query.child ?? ''))
  activeGroupKey.value = group.key
  activeChildKey.value = child.key
}

syncFromQuery()
watch(
  () => [route.query.group, route.query.child],
  () => {
    if (route.query.group || route.query.child) syncFromQuery()
  }
)

const selectedGroup = computed(() => groups.find((g) => g.key === activeGroupKey.value) ?? null)
const selectedChild = computed(
  () => selectedGroup.value?.children.find((c) => c.key === activeChildKey.value) ?? null
)

function selectGroup(group: ModuleGroup) {
  activeGroupKey.value = group.key
  activeChildKey.value = group.children[0]?.key ?? ''
  router.replace({ query: { group: group.key, child: activeChildKey.value } })
}

function selectChild(child: ModuleChild) {
  activeChildKey.value = child.key
  router.replace({ query: { group: activeGroupKey.value, child: child.key } })
}
</script>

<style scoped>
.browser {
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  background: var(--aies-surface);
  box-shadow: var(--aies-shadow);
  overflow: hidden;
}
.l2-bar {
  display: flex;
  align-items: stretch;
  gap: 4px;
  padding: 6px 10px 0;
  border-bottom: 1px solid var(--aies-border);
  background: var(--aies-surface);
  overflow-x: auto;
  scrollbar-width: thin;
}
.l2-item {
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
.browser-body {
  display: grid;
  grid-template-columns: 178px 1fr;
  min-height: 480px;
}
@media (max-width: 960px) {
  .browser-body {
    grid-template-columns: 150px 1fr;
  }
}
.l3-nav {
  border-right: 1px solid var(--aies-border);
  padding: 12px 8px;
  background: var(--aies-paper);
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
  min-width: 0;
  padding: 12px 14px 10px;
}
.placeholder {
  padding: 40px 0;
  text-align: center;
  font-size: 13px;
  color: var(--aies-ink3);
}
</style>
