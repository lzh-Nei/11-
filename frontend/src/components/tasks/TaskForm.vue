<template>
  <!-- 任务编辑表单（新建/编辑共用）：标题、班级、说明、截止时间、指标权重 -->
  <el-form label-position="top" class="task-form" @submit.prevent="submit">
    <el-alert
      v-if="lockStructure"
      type="info"
      show-icon
      :closable="false"
      class="lock-tip"
      title="该任务已有学生提交：班级与评分指标已锁定，仅可修改标题、说明和截止时间。"
    />
    <div class="form-grid">
      <el-form-item label="任务标题" required>
        <el-input v-model="draft.title" placeholder="如：实训作业1 · Word 文档格式检查" maxlength="64" />
      </el-form-item>
      <el-form-item label="所属班级" required>
        <el-select
          v-model="draft.classId"
          placeholder="选择班级"
          style="width: 100%"
          :disabled="lockStructure"
        >
          <el-option v-for="c in classes" :key="c.id" :label="c.className" :value="c.id" />
        </el-select>
      </el-form-item>
    </div>

    <el-form-item label="任务说明">
      <el-input
        v-model="draft.description"
        type="textarea"
        :rows="2"
        maxlength="500"
        placeholder="填写任务要求与提交说明"
      />
    </el-form-item>

    <el-form-item label="截止时间" required>
      <el-date-picker
        v-model="draft.deadline"
        type="datetime"
        format="YYYY-MM-DD HH:mm"
        value-format="YYYY-MM-DDTHH:mm:ss"
        placeholder="选择截止时间"
      />
    </el-form-item>

    <el-form-item label="评分指标（权重合计必须为 100%）">
      <div class="metric-editor">
        <ScrollArea max-height="240px" padded>
          <div class="metric-rows">
            <div v-for="(m, i) in draft.metrics" :key="i" class="metric-row">
              <el-input
                v-model="m.metricName"
                placeholder="指标名称"
                style="flex: 1"
                maxlength="32"
                :disabled="lockStructure"
              />
              <el-input-number
                v-model="m.weight"
                :min="1"
                :max="100"
                controls-position="right"
                style="width: 110px"
                :disabled="lockStructure"
              />
              <span class="metric-unit">%（第 {{ i + 1 }} 项）</span>
              <el-button
                text
                type="danger"
                :disabled="lockStructure || draft.metrics.length <= 1"
                @click="draft.metrics.splice(i, 1)"
                >删除</el-button
              >
            </div>
          </div>
        </ScrollArea>
        <div class="metric-foot">
          <el-button
            size="small"
            plain
            :disabled="lockStructure"
            @click="draft.metrics.push({ metricName: '', weight: 20, sortOrder: draft.metrics.length + 1 })"
          >
            + 添加指标
          </el-button>
          <span class="weight-sum" :class="{ bad: weightSum !== 100 }">合计 {{ weightSum }}%</span>
        </div>
      </div>
    </el-form-item>

    <div class="actions">
      <el-button type="primary" :loading="loading" :disabled="!canSubmit" @click="submit">
        {{ submitText }}
      </el-button>
      <span v-if="!classes.length" class="hint">暂无可用班级，请联系管理员先建立班级。</span>
    </div>
  </el-form>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { adminApi, type AdminClass } from '@/api/admin'
import type { TaskCreatePayload, TaskInfo } from '@/api/task'
import { useAuthStore } from '@/stores/auth'
import { toast } from '@/utils/toast'
import ScrollArea from '@/components/ui/ScrollArea.vue'

const props = withDefaults(
  defineProps<{
    initial?: TaskInfo | null
    submitText?: string
    loading?: boolean
    lockStructure?: boolean
  }>(),
  { initial: null, submitText: '保存任务', loading: false, lockStructure: false }
)
const emit = defineEmits<{ submit: [payload: TaskCreatePayload] }>()

const auth = useAuthStore()
const classes = ref<AdminClass[]>([])
const draft = reactive<TaskCreatePayload>({
  title: '',
  description: '',
  classId: 0,
  deadline: '',
  metrics: [
    { metricName: '', weight: 40, sortOrder: 1 },
    { metricName: '', weight: 35, sortOrder: 2 },
    { metricName: '', weight: 25, sortOrder: 3 }
  ]
})

const weightSum = computed(() => draft.metrics.reduce((sum, m) => sum + (m.weight || 0), 0))
const canSubmit = computed(
  () =>
    draft.title.trim().length > 0 &&
    draft.classId > 0 &&
    draft.deadline.length > 0 &&
    weightSum.value === 100
)

function reset() {
  draft.title = props.initial?.title ?? ''
  draft.description = props.initial?.description ?? ''
  draft.classId = props.initial?.classId ?? classes.value[0]?.id ?? 0
  draft.deadline = props.initial?.deadline ?? ''
  draft.metrics = props.initial?.metrics?.length
    ? props.initial.metrics.map((m) => ({
        metricName: m.metricName,
        weight: m.weight,
        sortOrder: m.sortOrder
      }))
    : [
        { metricName: '', weight: 40, sortOrder: 1 },
        { metricName: '', weight: 35, sortOrder: 2 },
        { metricName: '', weight: 25, sortOrder: 3 }
      ]
}

async function loadClasses() {
  try {
    const result = await adminApi.classes(1, 100)
    classes.value =
      auth.role === 'TEACHER'
        ? result.records.filter((c) => c.teacherId === auth.me?.id)
        : result.records
  } catch {
    classes.value = []
  }
}

async function submit() {
  if (!canSubmit.value) {
    toast.warning('请填写任务标题、班级、截止时间，并确保权重合计为 100%')
    return
  }
  if (draft.metrics.some((m) => !m.metricName.trim())) {
    toast.warning('请填写完整的指标名称')
    return
  }
  emit('submit', {
    title: draft.title.trim(),
    description: draft.description.trim(),
    classId: draft.classId,
    deadline: draft.deadline,
    metrics: draft.metrics.map((m, i) => ({
      metricName: m.metricName.trim(),
      weight: m.weight,
      sortOrder: i + 1
    }))
  })
}

watch(() => props.initial, reset)
onMounted(async () => {
  await loadClasses()
  reset()
})
</script>

<style scoped>
.task-form {
  max-width: 860px;
}
.lock-tip {
  margin-bottom: var(--aies-gap);
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
@media (max-width: 760px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
.metric-editor {
  width: 100%;
}
.metric-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.metric-unit {
  font-size: 12px;
  color: var(--aies-ink3);
}
.metric-foot {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 6px;
}
.weight-sum {
  font-size: 12px;
  color: #15803d;
}
.weight-sum.bad {
  color: #b91c1c;
}
.actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.hint {
  font-size: 12px;
  color: var(--aies-ink3);
}
</style>
