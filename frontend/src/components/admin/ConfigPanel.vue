<template>
  <!-- 系统配置（system:config）：只读运行快照（数据规模/认证邮件/AI 运行），不暴露密钥 -->
  <section class="panel">
    <div class="toolbar">
      <el-button :loading="loading" @click="load">刷新概况</el-button>
      <span class="dim">这里展示系统运行概况；登录与 AI 参数由对应配置模块维护</span>
    </div>

    <AppSkeleton v-if="loading" :rows="3" :row-height="80" />
    <ErrorState v-else-if="error" :message="error" @retry="load" />
    <div v-else-if="config" class="grid">
      <section class="card">
        <h3>数据规模</h3>
        <div class="counts">
          <div v-for="item in counts" :key="item.label" class="count">
            <b>{{ item.value }}</b>
            <small>{{ item.label }}</small>
          </div>
        </div>
      </section>

      <section class="card">
        <h3>登录与邮件</h3>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="邮件服务">
            <el-tag :type="config.smtpConfigured ? 'success' : 'warning'" size="small">
              {{ config.smtpConfigured ? '已配置' : '未配置' }}
            </el-tag>
            <span v-if="config.smtpHost" class="mono host">{{ config.smtpHost }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="图形验证码">
            {{ config.captchaExpireSeconds }} 秒有效 · {{ config.captchaCodeCount }} 位
          </el-descriptions-item>
          <el-descriptions-item label="邮箱验证码">
            {{ config.emailExpireSeconds }} 秒有效 · 冷却 {{ config.emailCooldownSeconds }} 秒
          </el-descriptions-item>
        </el-descriptions>
      </section>

      <section class="card">
        <h3>AI 运行</h3>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="当前模型">
            <span class="mono">{{ config.llmModel ?? '未配置' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="启用评分提示词版本">
            {{ config.activePromptVersion != null ? `v${config.activePromptVersion}` : '未启用' }}
          </el-descriptions-item>
          <el-descriptions-item label="参与校验的基准样本">
            {{ config.goldenActiveCount }} 个
          </el-descriptions-item>
        </el-descriptions>
      </section>
    </div>

    <p class="foot">
      以上为只读概况；登录凭证、邮件服务与文件存储密钥不在页面展示，如需调整请联系系统管理员。
    </p>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { adminApi, type AdminSystemConfig } from '@/api/admin'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import ErrorState from '@/components/ui/ErrorState.vue'

const loading = ref(false)
const error = ref<string | null>(null)
const config = ref<AdminSystemConfig | null>(null)

const counts = computed(() => {
  const c = config.value?.counts
  if (!c) return []
  return [
    { label: '账号', value: c.users },
    { label: '班级', value: c.classes },
    { label: '任务', value: c.tasks },
    { label: '提交', value: c.submissions },
    { label: '评价', value: c.evaluations }
  ]
})

async function load() {
  loading.value = true
  error.value = null
  try {
    config.value = await adminApi.systemConfig()
  } catch (e) {
    error.value = (e as { message?: string })?.message || '系统概况加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: var(--aies-gap);
}
.dim {
  font-size: 12px;
  color: var(--aies-ink3);
}
.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--aies-gap);
}
@media (max-width: 960px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
.card {
  background: var(--aies-paper);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  padding: var(--aies-gap-lg);
}
.card:first-child {
  grid-column: 1 / -1;
}
.card h3 {
  margin: 0 0 12px;
  font-size: 14.5px;
  color: var(--aies-ink);
}
.counts {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}
@media (max-width: 760px) {
  .counts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
.count {
  background: var(--aies-surface);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius);
  padding: 14px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.count b {
  font: 600 20px var(--aies-mono, monospace);
  color: var(--aies-ink);
}
.count small {
  font-size: 11.5px;
  color: var(--aies-ink3);
}
.mono {
  font-family: var(--aies-mono, monospace);
  font-size: 12.5px;
}
.host {
  margin-left: 8px;
  color: var(--aies-ink3);
}
.foot {
  margin-top: var(--aies-gap);
  font-size: 12px;
  color: var(--aies-ink3);
  line-height: 1.7;
}
</style>
