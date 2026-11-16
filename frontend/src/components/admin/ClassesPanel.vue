<template>
  <!-- 班级管理（class:manage，UC-34/35）：列表/创建/改名换教师/删除（有学生或任务时拒绝） -->
  <section class="panel">
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">新建班级</el-button>
      <el-button :loading="loading" @click="load">刷新</el-button>
      <span class="dim">共 {{ total }} 个班级</span>
    </div>

    <DataTableShell
      :loading="loading"
      :error="error"
      :rows="rows"
      :total="total"
      :page="page"
      :page-size="size"
      empty-text="还没有班级——新建第一个班级并绑定授课教师"
      empty-action-text="新建班级"
      @retry="load"
      @empty-action="openCreate"
      @page-change="(p: number) => ((page = p), load())"
    >
      <el-table :data="rows" stripe>
        <el-table-column prop="className" label="班级名称" min-width="160" />
        <el-table-column label="班级码" width="150">
          <template #default="{ row }"
            ><code class="mono">{{ row.classCode }}</code></template
          >
        </el-table-column>
        <el-table-column label="授课教师" width="140">
          <template #default="{ row }">{{ row.teacherName ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="学生数" width="90">
          <template #default="{ row }">{{ row.studentCount }}</template>
        </el-table-column>
        <el-table-column label="任务数" width="90">
          <template #default="{ row }">{{ row.taskCount }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" text type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </DataTableShell>

    <el-dialog v-model="dialog" :title="mode === 'create' ? '新建班级' : '编辑班级'" width="460px">
      <el-form label-position="top">
        <el-form-item label="班级名称" required>
          <el-input v-model="form.className" placeholder="如：软件工程11班" maxlength="64" />
        </el-form-item>
        <el-form-item v-if="mode === 'create'" label="班级码" required>
          <el-input
            v-model="form.classCode"
            placeholder="4-16位字母/数字/短横线（注册时使用）"
            maxlength="16"
          />
        </el-form-item>
        <el-form-item label="授课教师" required>
          <el-select v-model="form.teacherId" placeholder="选择具备 TEACHER 角色的账号" style="width: 100%">
            <el-option
              v-for="t in teachers"
              :key="t.id"
              :label="`${t.realName}（${t.username}）`"
              :value="t.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { adminApi, type AdminClass, type AdminTeacher } from '@/api/admin'
import { formatDateTime } from '@/utils/format'
import { toast } from '@/utils/toast'
import DataTableShell from '@/components/ui/DataTableShell.vue'

const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
const rows = ref<AdminClass[]>([])
const total = ref(0)
const page = ref(1)
const size = 10

const teachers = ref<AdminTeacher[]>([])
const dialog = ref(false)
const mode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const form = reactive({ className: '', classCode: '', teacherId: null as number | null })

async function load() {
  loading.value = true
  error.value = null
  try {
    const result = await adminApi.classes(page.value, size)
    rows.value = result.records
    total.value = result.total
  } catch (e) {
    error.value = (e as { message?: string })?.message || '班级清单加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function loadTeachers() {
  try {
    teachers.value = await adminApi.teachers()
  } catch {
    teachers.value = []
  }
}

async function openCreate() {
  await loadTeachers()
  mode.value = 'create'
  editingId.value = null
  form.className = ''
  form.classCode = ''
  form.teacherId = teachers.value[0]?.id ?? null
  dialog.value = true
}

async function openEdit(row: AdminClass) {
  await loadTeachers()
  mode.value = 'edit'
  editingId.value = row.id
  form.className = row.className
  form.classCode = row.classCode
  form.teacherId = row.teacherId
  dialog.value = true
}

async function save() {
  if (!form.className.trim()) {
    toast.warning('请填写班级名称')
    return
  }
  if (mode.value === 'create' && !/^[A-Za-z0-9-]{4,16}$/.test(form.classCode.trim())) {
    toast.warning('班级码需为4-16位字母/数字/短横线')
    return
  }
  if (!form.teacherId) {
    toast.warning('请选择授课教师')
    return
  }
  saving.value = true
  try {
    if (mode.value === 'create') {
      await adminApi.createClass({
        className: form.className.trim(),
        classCode: form.classCode.trim(),
        teacherId: form.teacherId
      })
      toast.success('班级已创建')
    } else if (editingId.value != null) {
      await adminApi.updateClass(editingId.value, {
        className: form.className.trim(),
        teacherId: form.teacherId
      })
      toast.success('班级已更新')
    }
    dialog.value = false
    await load()
  } catch {
    // 拦截器已提示（班级码重复/教师角色不匹配等）
  } finally {
    saving.value = false
  }
}

async function remove(row: AdminClass) {
  try {
    await ElMessageBox.confirm(
      `删除班级「${row.className}」：仅当班级下无学生且无任务时允许；删除不可恢复。`,
      '删除班级',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await adminApi.deleteClass(row.id)
    toast.success('班级已删除')
    await load()
  } catch {
    // 拦截器已提示占用原因
  }
}

onMounted(load)
</script>

<style scoped>
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
  font-size: 12.5px;
  background: var(--aies-paper);
  border: 1px solid var(--aies-border);
  border-radius: 4px;
  padding: 1px 6px;
}
</style>
