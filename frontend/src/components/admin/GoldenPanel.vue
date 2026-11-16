<template>
  <!-- 黄金数据集（golden:manage，UC-39/NFR-03）：样本清单、教师标注、最近一致率、参与回归启停 -->
  <section class="panel">
    <el-alert
      type="info"
      show-icon
      :closable="false"
      class="tip"
      title="基准样本用于检查 AI 评分与教师评分是否一致。当前可查看样本并控制是否参与校验。"
    />

    <div class="toolbar">
      <el-button :loading="loading" @click="load">刷新</el-button>
      <span class="dim">共 {{ total }} 个样本</span>
    </div>

    <DataTableShell
      :loading="loading"
      :error="error"
      :rows="rows"
      :total="total"
      :page="page"
      :page-size="size"
      empty-text="还没有基准样本"
      @retry="load"
      @page-change="(p: number) => ((page = p), load())"
    >
      <el-table :data="rows" stripe>
        <el-table-column label="样本文件" min-width="240">
          <template #default="{ row }">
            <span>{{ sampleName(row.sampleFileKey) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="教师总分" width="100">
          <template #default="{ row }"
            ><b class="mono">{{ row.teacherTotal }}</b></template
          >
        </el-table-column>
        <el-table-column label="指标标注" width="120">
          <template #default="{ row }">
            <el-popover v-if="row.teacherMetrics" placement="left" :width="260" trigger="click">
              <template #reference>
                <el-button size="small" text type="primary">查看标注</el-button>
              </template>
              <pre class="detail">{{ metricLines(row.teacherMetrics) }}</pre>
            </el-popover>
            <span v-else class="dim">—</span>
          </template>
        </el-table-column>
        <el-table-column label="最近符合度" width="120">
          <template #default="{ row }">
            <span v-if="row.lastConsistency == null" class="dim">未校验</span>
            <el-tag v-else :type="row.lastConsistency >= 0.7 ? 'success' : 'warning'" size="small">
              {{ (row.lastConsistency * 100).toFixed(1) }}%
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="参与校验" width="110">
          <template #default="{ row }">
            <el-tag :type="row.active === 1 ? 'success' : 'info'" size="small">
              {{ row.active === 1 ? '参与' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text :type="row.active === 1 ? 'danger' : 'success'" @click="toggle(row)">
              {{ row.active === 1 ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </DataTableShell>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { adminApi, type AdminGoldenSample } from '@/api/admin'
import { toast } from '@/utils/toast'
import DataTableShell from '@/components/ui/DataTableShell.vue'

const loading = ref(false)
const error = ref<string | null>(null)
const rows = ref<AdminGoldenSample[]>([])
const total = ref(0)
const page = ref(1)
const size = 10

function sampleName(key: string): string {
  return key.substring(key.lastIndexOf('/') + 1) || key
}

function metricLines(metrics: Record<string, number> | null): string {
  return Object.entries(metrics ?? {})
    .map(([name, score]) => `${name}：${score} 分`)
    .join('\n')
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const result = await adminApi.goldenSamples(page.value, size)
    rows.value = result.records
    total.value = result.total
  } catch (e) {
    error.value = (e as { message?: string })?.message || '基准样本加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function toggle(row: AdminGoldenSample) {
  const next = row.active === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(
      next === 0
        ? `停用样本 ${sampleName(row.sampleFileKey)} 后，后续评分一致性校验不再统计该样本。`
        : `启用样本 ${sampleName(row.sampleFileKey)} 后，将参与下一轮评分一致性校验。`,
      next === 0 ? '停用基准样本' : '启用基准样本',
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await adminApi.setGoldenActive(row.id, next)
    toast.success(next === 1 ? '样本已启用' : '样本已停用')
    await load()
  } catch {
    // 拦截器已提示
  }
}

onMounted(load)
</script>

<style scoped>
.tip {
  margin-bottom: var(--aies-gap);
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: var(--aies-gap);
}
.dim {
  font-size: 12px;
  color: var(--aies-ink3);
}
.mono {
  font-family: var(--aies-mono, monospace);
  font-size: 12px;
}
.detail {
  margin: 0;
  font: 11.5px/1.7 var(--aies-mono, monospace);
  color: var(--aies-ink2);
  white-space: pre-wrap;
}
</style>
