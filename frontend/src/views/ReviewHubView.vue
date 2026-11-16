<template>
  <!-- 评价与复核（教师）：本人任务下的评价列表 → 同页展开评分详情、改分与历史版本 -->
  <PageShell :bare="embedded">
    <PageHeader v-if="!embedded" title="评价与复核" subtitle="选择一条评价，查看评分并完成复核">
      <BackButton />
      <el-button @click="router.push('/modules?group=teaching&child=task-list')">去任务列表</el-button>
    </PageHeader>

    <div class="head">
      <div>
        <h3>评价与复核历史</h3>
        <p class="hint">查看本人任务下学生的确认、改分与历史版本；选择一条即可在下方完成复核。</p>
      </div>
      <div class="tools">
        <el-select v-model="status" placeholder="全部状态" style="width: 140px" @change="reload">
          <el-option label="全部状态" value="" />
          <el-option label="待评价" value="PENDING" />
          <el-option label="已出分" value="AI_DONE" />
          <el-option label="已复核" value="REVIEWED" />
          <el-option label="评价失败" value="FAILED" />
        </el-select>
        <el-button size="small" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <DataTableShell
      :loading="loading"
      :error="error"
      :rows="rows"
      :total="total"
      :page="page"
      :page-size="size"
      empty-text="还没有可复核的评价——学生提交作业并完成评价后，这里会出现记录"
      @retry="load"
      @page-change="(p: number) => ((page = p), load())"
    >
      <el-table :data="rows" stripe @row-click="openReview">
        <el-table-column label="任务" min-width="180">
          <template #default="{ row }">{{ row.taskTitle ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="学生" width="120">
          <template #default="{ row }">{{ row.studentName ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="教师" width="120">
          <template #default="{ row }">{{ row.teacherName ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }"><StatusTag :status="row.status" /></template>
        </el-table-column>
        <el-table-column label="总分" width="90">
          <template #default="{ row }">
            <span class="mono">{{ row.totalScore ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="版本" width="80">
          <template #default="{ row }">
            <span class="mono">v{{ row.currentVersionNo ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="更新时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click.stop="openReview(row)">
              查看与复核
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </DataTableShell>

    <section v-if="selected" class="workbench">
      <div class="workbench-head">
        <h3>
          {{ selected.taskTitle || '评价详情' }}
          <span v-if="selected.studentName" class="sub">· {{ selected.studentName }}</span>
        </h3>
        <el-button link type="primary" size="small" @click="selected = null">收起</el-button>
      </div>
      <Evaluate :key="selected.id" :evaluation-id="selected.id" embedded />
    </section>
  </PageShell>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { adminApi, type AdminEvaluation } from '@/api/admin'
import { formatDateTime } from '@/utils/format'
import PageShell from '@/components/ui/PageShell.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import BackButton from '@/components/ui/BackButton.vue'
import DataTableShell from '@/components/ui/DataTableShell.vue'
import StatusTag from '@/components/ui/StatusTag.vue'
import Evaluate from '@/views/Evaluate.vue'

withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false })

const router = useRouter()
const loading = ref(false)
const error = ref<string | null>(null)
const rows = ref<AdminEvaluation[]>([])
const total = ref(0)
const page = ref(1)
const size = 10
const status = ref('')
const selected = ref<AdminEvaluation | null>(null)

async function load() {
  loading.value = true
  error.value = null
  try {
    const result = await adminApi.evaluations({
      page: page.value,
      size,
      status: status.value || undefined
    })
    rows.value = result.records
    total.value = result.total
  } catch (e) {
    error.value = (e as { message?: string })?.message || '评价记录加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 1
  load()
}

function openReview(row: AdminEvaluation) {
  selected.value = row
}

onMounted(load)
</script>

<style scoped>
.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: var(--aies-gap);
  flex-wrap: wrap;
}
.head h3 {
  margin: 0;
  font-size: 15px;
  color: var(--aies-ink);
}
.hint {
  margin: 6px 0 0;
  font-size: 12.5px;
  color: var(--aies-ink3);
  line-height: 1.7;
}
.tools {
  display: flex;
  align-items: center;
  gap: 8px;
}
.workbench {
  margin-top: var(--aies-gap);
  padding: var(--aies-gap);
  background: var(--aies-surface);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  box-shadow: var(--aies-shadow);
}
.workbench-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}
.workbench-head h3 {
  margin: 0;
  font-size: 14px;
  color: var(--aies-ink);
}
.sub {
  font-weight: 400;
  color: var(--aies-ink3);
}
.mono {
  font-family: var(--aies-mono, monospace);
  font-size: 12.5px;
}
</style>
