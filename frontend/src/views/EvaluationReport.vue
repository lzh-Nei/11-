<template>
  <div class="page">
    <el-card v-loading="loading">
      <template #header>
        <div class="card-head">
          <span>评价报告（UC-21 / E-01）</span>
          <el-button size="small" @click="router.back()">返回</el-button>
        </div>
      </template>

      <template v-if="evaluation">
        <!-- PENDING：评价进行中（10s 轮询，LLD §4.3 步骤7 学生端刷新口径） -->
        <el-empty v-if="evaluation.status === 'PENDING'" description="AI评价进行中，页面每10秒自动刷新…" :image-size="100" />

        <!-- FAILED：40003 重试耗尽（LLD：教师端人工入口提示） -->
        <template v-else-if="evaluation.status === 'FAILED'">
          <el-result icon="error" title="AI评价失败（40003）"
            sub-title="模型输出多次未通过格式校验，已登记留痕；请联系教师人工评分（UC-24旁路）。" />
        </template>

        <!-- AI_DONE / REVIEWED：报告主体 -->
        <template v-else>
          <div class="score-band">
            <div class="total">
              <div class="total-num">{{ evaluation.totalScore }}</div>
              <div class="total-label">总分（100分制）</div>
            </div>
            <div class="metrics">
              <div v-for="(score, name) in evaluation.metricScores" :key="name" class="metric-row">
                <span class="metric-name">{{ name }}</span>
                <el-progress :percentage="Number(((score / 100) * 100).toFixed(1))" :stroke-width="14"
                  :format="() => `${score}`" />
              </div>
            </div>
          </div>

          <div class="section">
            <div class="section-title">AI评语</div>
            <div class="comment">{{ evaluation.aiComment }}</div>
          </div>

          <el-descriptions :column="3" border class="meta">
            <el-descriptions-item label="评价状态">
              <el-tag :type="evalTag(evaluation.status)" size="small">{{ evaluation.status }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="生效版本">v{{ evaluation.currentVersionNo }}</el-descriptions-item>
            <el-descriptions-item label="更新时间">{{ fmt(evaluation.updateTime) }}</el-descriptions-item>
          </el-descriptions>
          <div class="audit-note">复核流转（教师确认/改分 UC-22）属后续迭代；当前为 AI 初评 v1。</div>
        </template>
      </template>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { evaluationApi, type EvaluationVO } from '@/api/evaluation'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const evaluation = ref<EvaluationVO | null>(null)
let timer: number | undefined

function evalTag(s: string) {
  if (s === 'AI_DONE' || s === 'REVIEWED') return 'success'
  if (s === 'FAILED') return 'danger'
  return 'info'
}
function fmt(s: string) {
  return s ? s.replace('T', ' ').slice(0, 16) : '—'
}

async function load() {
  loading.value = true
  try {
    evaluation.value = await evaluationApi.detail(Number(route.params.id))
    // PENDING → 10s 轮询；AI_DONE/REVIEWED/FAILED 停止轮询
    if (evaluation.value.status === 'PENDING') {
      if (timer === undefined) timer = window.setInterval(load, 10000)
    } else if (timer !== undefined) {
      window.clearInterval(timer)
      timer = undefined
    }
  } finally {
    loading.value = false
  }
}
onMounted(load)
onBeforeUnmount(() => {
  if (timer !== undefined) window.clearInterval(timer)
})
</script>

<style scoped>
.page {
  max-width: 860px;
  margin: 16px auto;
  padding: 0 12px;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.score-band {
  display: flex;
  gap: 32px;
  align-items: center;
  padding: 8px 4px 20px;
}
.total {
  text-align: center;
  min-width: 140px;
}
.total-num {
  font-size: 56px;
  font-weight: 700;
  color: #409eff;
  line-height: 1.1;
}
.total-label {
  color: #909399;
  font-size: 13px;
}
.metrics {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.metric-name {
  display: inline-block;
  width: 110px;
  color: #606266;
}
.section {
  margin: 8px 0 16px;
}
.section-title {
  font-weight: 600;
  color: #606266;
  margin-bottom: 8px;
}
.comment {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 12px 16px;
  line-height: 1.7;
  white-space: pre-wrap;
  color: #303133;
}
.meta {
  margin-top: 8px;
}
.audit-note {
  margin-top: 12px;
  color: #c0c4cc;
  font-size: 12px;
}
</style>
