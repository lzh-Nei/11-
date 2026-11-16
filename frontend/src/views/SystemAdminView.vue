<template>
  <!-- 系统管理（管理员模块）：如实展示 M3 规划范围与预留权限点，不伪造不可用功能 -->
  <PageShell>
    <PageHeader title="系统管理" subtitle="管理员模块 · M3 规划范围（端点未开放，预留权限点已就绪）">
      <el-button @click="router.push('/account')">查看权限清单</el-button>
    </PageHeader>

    <el-alert type="warning" show-icon :closable="false" class="tip">
      <template #title>
        本模块为 M3 管理端规划清单：后端尚未开放管理端点（用户/角色/班级/配置/审计），
        为避免误导，当前只做范围与权限点对照，不提供可点击的伪功能。报表聚合端点仅对教师开放 （C1
        范围收敛），管理员工作台不展示教学统计。
      </template>
    </el-alert>

    <section class="usable">
      <h2>当前可用的管理能力</h2>
      <div class="usable-list">
        <button type="button" class="usable-card" @click="router.push('/tasks/manage')">
          <NavIcon name="manage" />
          <b>任务管理</b>
          <small>创建/发布任务与指标配置</small>
        </button>
        <button type="button" class="usable-card" @click="router.push('/review')">
          <NavIcon name="review" />
          <b>评价复核</b>
          <small>复核工作台入口</small>
        </button>
        <button type="button" class="usable-card" @click="router.push('/files')">
          <NavIcon name="files" />
          <b>文件中心</b>
          <small>存储产物与流式下载</small>
        </button>
        <button type="button" class="usable-card" @click="router.push('/account')">
          <NavIcon name="user" />
          <b>个人中心</b>
          <small>账号档案、权限与安全操作</small>
        </button>
      </div>
    </section>

    <section class="planned">
      <h2>M3 规划模块</h2>
      <div class="planned-grid">
        <article v-for="m in modules" :key="m.code" class="plan-card">
          <div class="plan-head">
            <NavIcon :name="m.icon" />
            <b>{{ m.title }}</b>
            <span class="reserved">预留</span>
          </div>
          <p>{{ m.desc }}</p>
          <code>{{ m.code }}</code>
        </article>
      </div>
    </section>
  </PageShell>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import PageShell from '@/components/ui/PageShell.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import NavIcon from '@/components/ui/NavIcon.vue'

interface PlannedModule {
  title: string
  desc: string
  code: string
  icon: string
}

const router = useRouter()
const auth = useAuthStore()

const ALL_MODULES: PlannedModule[] = [
  {
    title: '用户管理',
    desc: '账号启用/停用、重置密码、角色分配（UC-06/07/09）',
    code: 'user:manage',
    icon: 'user'
  },
  {
    title: '角色权限管理',
    desc: '角色-权限矩阵维护与变更留痕（UC-31/32）',
    code: 'role:manage',
    icon: 'review'
  },
  {
    title: '班级管理',
    desc: '班级创建、班级码发放、学生归属（UC-34/35）',
    code: 'class:manage',
    icon: 'tasks'
  },
  {
    title: '系统配置',
    desc: 'SMTP、LLM 参数、Prompt 模板、黄金数据集（UC-33/37/38/39）',
    code: 'system:config',
    icon: 'system'
  },
  {
    title: '审计日志',
    desc: '登录/复核/配置变更的留痕检索（UC-40）',
    code: 'audit:view',
    icon: 'archive'
  },
  {
    title: 'LLM 参数',
    desc: '模型地址、超时、温度与降级策略运行配置',
    code: 'llm:config',
    icon: 'chart'
  },
  {
    title: 'Prompt 模板',
    desc: '评分提示词版本管理与灰度切换',
    code: 'prompt:manage',
    icon: 'manage'
  },
  {
    title: '黄金数据集',
    desc: '锚例维护、扩容与一致率回归（NFR-03）',
    code: 'golden:manage',
    icon: 'evaluation'
  }
]

/* 只展示当前管理员实际被授予的预留权限点（矩阵口径） */
const modules = computed(() => ALL_MODULES.filter((m) => (auth.me?.permissions ?? []).includes(m.code)))
</script>

<style scoped>
.tip {
  margin-bottom: var(--aies-gap);
}
h2 {
  font-size: 15px;
  color: var(--aies-ink);
  margin: 0 0 10px;
}
.usable-list {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}
@media (max-width: 1024px) {
  .usable-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
.usable-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  padding: 14px;
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  background: var(--aies-surface);
  box-shadow: var(--aies-shadow);
  cursor: pointer;
  font-family: inherit;
  text-align: left;
  color: var(--aies-brand);
  transition:
    box-shadow var(--aies-t),
    transform var(--aies-t),
    border-color var(--aies-t);
}
.usable-card:hover {
  box-shadow: var(--aies-shadow-hover);
  transform: var(--aies-lift);
  border-color: var(--aies-brand);
}
.usable-card b {
  font-size: 13.5px;
  color: var(--aies-ink);
}
.usable-card small {
  font-size: 11.5px;
  color: var(--aies-ink3);
  line-height: 1.5;
}
.planned {
  margin-top: var(--aies-gap-lg);
}
.planned-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
@media (max-width: 900px) {
  .planned-grid {
    grid-template-columns: 1fr;
  }
}
.plan-card {
  border: 1px dashed var(--aies-border);
  border-radius: var(--aies-radius-lg);
  padding: 14px 16px;
  background: var(--aies-surface);
}
.plan-head {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--aies-ink2);
}
.plan-head b {
  flex: 1;
  font-size: 13.5px;
  color: var(--aies-ink);
}
.reserved {
  font-size: 10.5px;
  color: var(--aies-ink3);
  border: 1px dashed var(--aies-border);
  border-radius: 999px;
  padding: 1px 7px;
}
.plan-card p {
  margin: 8px 0;
  font-size: 12px;
  color: var(--aies-ink3);
  line-height: 1.7;
}
.plan-card code {
  font-family: var(--aies-mono, monospace);
  font-size: 11px;
  color: var(--aies-ink3);
}
</style>
