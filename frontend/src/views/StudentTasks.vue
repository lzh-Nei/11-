<template>
  <div class="page">
    <el-card>
      <template #header>
        <div class="card-head">
          <span>我的任务（GET /api/tasks，学生视角：本班已发布）</span>
          <el-button size="small" @click="router.push('/')">返回首页</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="tasks" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="任务名称" min-width="180" />
        <el-table-column label="指标集" min-width="220">
          <template #default="{ row }">
            <el-tag
              v-for="m in row.metrics"
              :key="m.id"
              size="small"
              type="info"
              effect="plain"
              class="tag-gap"
            >
              {{ m.metricName }} {{ m.weight }}%
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="截止时间" width="170">
          <template #default="{ row }">
            <span :class="{ overdue: isOverdue(row), urgent: isUrgent(row) }">{{ fmt(row.deadline) }}</span>
            <el-tag v-if="isOverdue(row)" size="small" type="danger" class="tag-gap">已截止</el-tag>
            <el-tag v-else-if="isUrgent(row)" size="small" type="warning" class="tag-gap">临近</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              size="small"
              :disabled="isOverdue(row) || row.status === 'CLOSED'"
              @click="router.push(`/tasks/${row.id}/submit`)"
            >
              提交作业
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        class="pager"
        @current-change="load"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { taskApi, type TaskInfo } from '@/api/task'

const router = useRouter()
const loading = ref(false)
const tasks = ref<TaskInfo[]>([])
const page = ref(1)
const size = 10
const total = ref(0)

function fmt(s: string) {
  return s ? s.replace('T', ' ').slice(0, 16) : '—'
}
function isOverdue(row: TaskInfo) {
  return row.status === 'CLOSED' || new Date(row.deadline).getTime() < Date.now()
}
function isUrgent(row: TaskInfo) {
  return !isOverdue(row) && new Date(row.deadline).getTime() - Date.now() < 24 * 3600 * 1000
}

async function load() {
  loading.value = true
  try {
    const result = await taskApi.list(page.value, size, 'PUBLISHED')
    tasks.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<style scoped>
.page {
  max-width: 1080px;
  margin: 16px auto;
  padding: 0 12px;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
.tag-gap {
  margin-right: 4px;
}
.overdue {
  color: #f56c6c;
}
.urgent {
  color: #e6a23c;
}
</style>
