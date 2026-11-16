<template>
  <!-- 系统管理（管理员模块，M3）：八组真实管理端点，全部写操作留痕（S-09） -->
  <PageShell>
    <PageHeader
      title="系统管理"
      subtitle="M3 管理端：用户与角色权限、班级、审计日志、AI 运行配置（真实端点）"
    >
      <el-button size="small" @click="router.push('/account')">查看权限清单</el-button>
    </PageHeader>

    <el-tabs v-model="active" tab-position="left" class="admin-tabs">
      <el-tab-pane v-for="tab in tabs" :key="tab.name" :name="tab.name" lazy>
        <template #label>
          <span class="tab-label"><NavIcon :name="tab.icon" />{{ tab.label }}</span>
        </template>
        <component :is="tab.component" />
      </el-tab-pane>
    </el-tabs>
  </PageShell>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import PageShell from '@/components/ui/PageShell.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import NavIcon from '@/components/ui/NavIcon.vue'
import UsersPanel from '@/components/admin/UsersPanel.vue'
import RolesPanel from '@/components/admin/RolesPanel.vue'
import ClassesPanel from '@/components/admin/ClassesPanel.vue'
import AuditLogsPanel from '@/components/admin/AuditLogsPanel.vue'
import LlmConfigPanel from '@/components/admin/LlmConfigPanel.vue'
import PromptPanel from '@/components/admin/PromptPanel.vue'
import GoldenPanel from '@/components/admin/GoldenPanel.vue'
import ConfigPanel from '@/components/admin/ConfigPanel.vue'

const router = useRouter()
const active = ref('users')

/* 管理模块清单：与后端 AdminController 分组一一对应（权限点见个人中心） */
const tabs = [
  { name: 'users', label: '用户管理', icon: 'user', component: UsersPanel },
  { name: 'roles', label: '角色权限', icon: 'review', component: RolesPanel },
  { name: 'classes', label: '班级管理', icon: 'tasks', component: ClassesPanel },
  { name: 'audit', label: '审计日志', icon: 'archive', component: AuditLogsPanel },
  { name: 'llm', label: 'LLM 参数', icon: 'chart', component: LlmConfigPanel },
  { name: 'prompts', label: 'Prompt 模板', icon: 'manage', component: PromptPanel },
  { name: 'golden', label: '黄金数据集', icon: 'evaluation', component: GoldenPanel },
  { name: 'config', label: '系统配置', icon: 'system', component: ConfigPanel }
]
</script>

<style scoped>
.admin-tabs {
  background: var(--aies-surface);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  box-shadow: var(--aies-shadow);
  padding: 10px 16px;
  min-height: 540px;
}
:deep(.el-tabs__header) {
  min-width: 148px;
}
:deep(.el-tabs__item) {
  height: 44px;
  font-size: 13.5px;
  justify-content: flex-start;
}
:deep(.el-tabs__content) {
  padding: 6px 4px 10px 18px;
}
.tab-label {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
