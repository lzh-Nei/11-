<template>
  <!-- 用户管理（user:manage）：检索 / 新建 / 编辑 / 删除 / 禁用启用 / 角色分配（UC-06/07/09） -->
  <section class="panel">
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">新建用户</el-button>
      <el-input
        v-model="keyword"
        class="search"
        placeholder="按学号/工号或姓名筛选"
        clearable
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-select v-model="statusFilter" placeholder="全部状态" style="width: 130px" @change="reload">
        <el-option label="全部状态" value="" />
        <el-option label="正常" value="1" />
        <el-option label="已禁用" value="0" />
      </el-select>
      <el-select v-model="roleFilter" placeholder="全部角色" style="width: 140px" @change="reload">
        <el-option label="全部角色" value="" />
        <el-option v-for="r in roles" :key="r.roleCode" :label="roleLabel(r.roleCode)" :value="r.roleCode" />
      </el-select>
      <el-button :loading="loading" @click="reload">查询</el-button>
      <span class="dim">共 {{ total }} 个账号</span>
    </div>

    <DataTableShell
      :loading="loading"
      :error="error"
      :rows="rows"
      :total="total"
      :page="page"
      :page-size="size"
      empty-text="没有匹配的账号"
      @retry="load"
      @page-change="(p: number) => ((page = p), load())"
    >
      <el-table :data="rows" stripe>
        <el-table-column label="账号" min-width="150">
          <template #default="{ row }">
            <b class="mono">{{ row.username }}</b>
            <el-tag v-if="row.id === auth.me?.id" size="small" type="info" class="ml">当前账号</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="realName" label="姓名" width="110" />
        <el-table-column label="角色" width="150">
          <template #default="{ row }">
            <el-tag
              v-for="code in row.roleCodes"
              :key="code"
              size="small"
              :type="roleTagType(code)"
              class="mr"

            >
              {{ roleLabel(code) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="班级" width="130">
          <template #default="{ row }">{{ row.className ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="邮箱" min-width="160">
          <template #default="{ row }">{{ row.email ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="首登改密" width="100">
          <template #default="{ row }">
            <span :class="{ warn: row.firstLogin === 1 }">{{ row.firstLogin === 1 ? '待改密' : '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              text
              type="primary"
              :disabled="isProtected(row)"
              :title="protectedReason(row)"
              @click="openEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              size="small"
              text
              type="primary"
              :disabled="isProtected(row)"
              :title="protectedReason(row)"
              @click="openRoles(row)"
            >
              角色
            </el-button>
            <el-button
              size="small"
              text
              :type="row.status === 1 ? 'danger' : 'success'"
              :disabled="isProtected(row)"
              :title="protectedReason(row)"
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button
              size="small"
              text
              type="danger"
              :disabled="isProtected(row)"
              :title="protectedReason(row)"
              @click="removeUser(row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </DataTableShell>

    <!-- 新建 / 编辑用户 -->
    <el-dialog
      v-model="editorDialog"
      :title="editorMode === 'create' ? '新建用户' : '编辑用户'"
      width="560px"
    >
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="学号 / 工号" required>
            <el-input
              v-model="form.username"
              :disabled="editorMode === 'edit'"
              placeholder="4-20位字母、数字或下划线"
              maxlength="20"
            />
          </el-form-item>
          <el-form-item label="姓名" required>
            <el-input v-model="form.realName" placeholder="请输入姓名" maxlength="32" />
          </el-form-item>
        </div>
        <el-form-item v-if="editorMode === 'create'" label="初始密码" required>
          <el-input
            v-model="form.password"
            type="password"
            placeholder="至少8位，同时含字母和数字"
            show-password
            autocomplete="new-password"
          />
          <span class="field-hint">新账号首次登录会被要求修改初始密码</span>
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="邮箱（选填）">
            <el-input v-model="form.email" placeholder="用于登录验证与找回密码" maxlength="64" />
          </el-form-item>
          <el-form-item label="手机号（选填）">
            <el-input v-model="form.phone" placeholder="11 位手机号" maxlength="11" />
          </el-form-item>
        </div>
        <div class="form-grid">
          <el-form-item label="班级（选填）">
            <el-select v-model="form.classId" clearable placeholder="不绑定班级" style="width: 100%">
              <el-option
                v-for="c in classes"
                :key="c.id"
                :label="`${c.className}（${c.classCode}）`"
                :value="c.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-if="editorMode === 'create'" label="角色" required>
            <el-checkbox-group v-model="form.roleCodes">
              <el-checkbox v-for="r in assignableRoles" :key="r.roleCode" :value="r.roleCode">
                {{ roleLabel(r.roleCode) }}
              </el-checkbox>
            </el-checkbox-group>
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="editorDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEditor">
          {{ editorMode === 'create' ? '创建用户' : '保存修改' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 角色分配 -->
    <el-dialog v-model="roleDialog" title="分配角色" width="440px">
      <el-alert
        v-if="editing"
        :closable="false"
        type="info"
        :title="`账号：${editing.username}（${editing.realName}）· 角色变更保存后立即生效`"
        class="mb"
      />
      <el-checkbox-group v-model="draftRoles">
        <el-checkbox v-for="r in roles" :key="r.roleCode" :value="r.roleCode" class="role-check">
          {{ roleLabel(r.roleCode) }} · {{ r.roleName }}
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRoles">保存角色</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { adminApi, type AdminClass, type AdminRole, type AdminUser } from '@/api/admin'
import { useAuthStore } from '@/stores/auth'
import { roleLabel } from '@/constants/admin'
import { toast } from '@/utils/toast'
import DataTableShell from '@/components/ui/DataTableShell.vue'

const auth = useAuthStore()

const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
const rows = ref<AdminUser[]>([])
const total = ref(0)
const page = ref(1)
const size = 10
const keyword = ref('')
const statusFilter = ref('')
const roleFilter = ref('')

const roles = ref<AdminRole[]>([])
const classes = ref<AdminClass[]>([])
const assignableRoles = computed(() => roles.value.filter((r) => r.roleCode !== 'ADMIN'))

const roleDialog = ref(false)
const editing = ref<AdminUser | null>(null)
const draftRoles = ref<string[]>([])

const editorDialog = ref(false)
const editorMode = ref<'create' | 'edit'>('create')
const editingUser = ref<AdminUser | null>(null)
const form = reactive({
  username: '',
  realName: '',
  password: '',
  email: '',
  phone: '',
  classId: null as number | null,
  roleCodes: [] as string[]
})

const emailValid = computed(
  () => !form.email.trim() || /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(form.email.trim())
)
const phoneValid = computed(() => !form.phone.trim() || /^1[3-9]\d{9}$/.test(form.phone.trim()))

function roleTagType(code: string): 'success' | 'warning' | 'danger' | 'info' {
  if (code === 'ADMIN') return 'danger'
  if (code === 'TEACHER') return 'warning'
  if (code === 'STUDENT') return 'success'
  return 'info'
}

function isProtected(row: AdminUser): boolean {
  return row.id === auth.me?.id || row.roleCodes.includes('ADMIN')
}

function protectedReason(row: AdminUser): string {
  if (row.id === auth.me?.id) return '不能修改当前登录账号'
  if (row.roleCodes.includes('ADMIN')) return '管理员账号受保护（请在个人中心维护资料）'
  return ''
}

async function loadRoles() {
  if (roles.value.length) return
  try {
    roles.value = await adminApi.roles()
  } catch {
    roles.value = []
  }
}

async function loadClasses() {
  if (classes.value.length) return
  try {
    const result = await adminApi.classes(1, 100)
    classes.value = result.records
  } catch {
    classes.value = []
  }
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const params: Parameters<typeof adminApi.users>[0] = { page: page.value, size }
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    if (statusFilter.value !== '') params.status = Number(statusFilter.value)
    if (roleFilter.value) params.roleCode = roleFilter.value
    const result = await adminApi.users(params)
    rows.value = result.records
    total.value = result.total
  } catch (e) {
    error.value = (e as { message?: string })?.message || '用户清单加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 1
  load()
}

function resetForm() {
  form.username = ''
  form.realName = ''
  form.password = ''
  form.email = ''
  form.phone = ''
  form.classId = null
  form.roleCodes = []
}

async function openCreate() {
  await Promise.all([loadRoles(), loadClasses()])
  resetForm()
  if (assignableRoles.value.some((r) => r.roleCode === 'STUDENT')) {
    form.roleCodes = ['STUDENT']
  }
  editorMode.value = 'create'
  editingUser.value = null
  editorDialog.value = true
}

async function openEdit(row: AdminUser) {
  await loadClasses()
  editorMode.value = 'edit'
  editingUser.value = row
  form.username = row.username
  form.realName = row.realName ?? ''
  form.password = ''
  form.email = row.email ?? ''
  form.phone = row.phone ?? ''
  form.classId = row.classId
  form.roleCodes = [...row.roleCodes]
  editorDialog.value = true
}

function validateEditor(): boolean {
  if (editorMode.value === 'create' && !/^[A-Za-z0-9_]{4,20}$/.test(form.username.trim())) {
    toast.warning('学号/工号需为4-20位字母、数字或下划线')
    return false
  }
  if (!form.realName.trim()) {
    toast.warning('请填写姓名')
    return false
  }
  if (editorMode.value === 'create' && !/^(?=.*[A-Za-z])(?=.*\d).{8,32}$/.test(form.password)) {
    toast.warning('初始密码需至少8位，且同时包含字母和数字')
    return false
  }
  if (!emailValid.value) {
    toast.warning('邮箱格式不正确')
    return false
  }
  if (!phoneValid.value) {
    toast.warning('手机号格式不正确')
    return false
  }
  if (editorMode.value === 'create' && form.roleCodes.length === 0) {
    toast.warning('至少选择一个角色')
    return false
  }
  return true
}

async function saveEditor() {
  if (!validateEditor()) return
  saving.value = true
  try {
    if (editorMode.value === 'create') {
      await adminApi.createUser({
        username: form.username.trim(),
        realName: form.realName.trim(),
        password: form.password,
        email: form.email.trim(),
        phone: form.phone.trim(),
        classId: form.classId ?? null,
        roleCodes: form.roleCodes
      })
      toast.success('用户已创建，初始密码首次登录需修改')
    } else if (editingUser.value) {
      await adminApi.updateUser(editingUser.value.id, {
        realName: form.realName.trim(),
        email: form.email.trim(),
        phone: form.phone.trim(),
        classId: form.classId ?? null
      })
      toast.success('用户资料已更新')
    }
    editorDialog.value = false
    await load()
  } catch {
    // 拦截器已提示后端 message
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: AdminUser) {
  const next = row.status === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(
      next === 0
        ? `禁用 ${row.username} 后：该账号将无法登录，已登录的会话也会失效。`
        : `启用 ${row.username} 后可正常登录。`,
      next === 0 ? '禁用账号' : '启用账号',
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await adminApi.updateUserStatus(row.id, next)
    toast.success(next === 1 ? '账号已启用' : '账号已禁用')
    await load()
  } catch {
    // 拦截器已提示
  }
}

async function removeUser(row: AdminUser) {
  let impact
  try {
    impact = await adminApi.userDeletionImpact(row.id)
  } catch {
    return
  }
  if (impact.protectedAccount) {
    toast.warning(impact.protectedReason || '该账号受保护，不能删除')
    return
  }
  if (impact.totalRecords === 0) {
    try {
      await ElMessageBox.confirm(
        `删除「${row.realName}（${row.username}）」？该账号当前没有业务记录，删除不可恢复。`,
        '删除用户',
        { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
      )
    } catch {
      return
    }
    try {
      await adminApi.deleteUser(row.id)
      toast.success('用户已删除')
      await load()
    } catch {
      // 拦截器已提示
    }
    return
  }

  const parts: string[] = []
  if (impact.ownedClasses) parts.push(`班级 ${impact.ownedClasses} 个`)
  if (impact.ownedTasks) parts.push(`任务 ${impact.ownedTasks} 个`)
  if (impact.ownedSubmissions) parts.push(`提交 ${impact.ownedSubmissions} 条`)
  if (impact.ownedEvaluationResults) parts.push(`评价 ${impact.ownedEvaluationResults} 条`)
  if (impact.ownedEvaluationHistories) parts.push(`评价历史 ${impact.ownedEvaluationHistories} 条`)
  if (impact.appeals) parts.push(`申诉 ${impact.appeals} 条`)
  if (impact.auditLogs) parts.push(`审计记录 ${impact.auditLogs} 条`)
  if (impact.reportExports) parts.push(`报表导出任务 ${impact.reportExports} 条`)
  if (impact.reviewReferences) parts.push(`复核痕迹 ${impact.reviewReferences} 处（将匿名化保留）`)
  if (impact.unboundStudents) parts.push(`${impact.unboundStudents} 名学生将解除班级绑定`)
  const summary = parts.length ? parts.join('、') : '关联记录'
  try {
    await ElMessageBox.confirm(
      `「${row.realName}（${row.username}）」存在 ${impact.totalRecords} 条关联记录：${summary}。\n\n强制删除会先清理这些记录，再删除账号；操作不可恢复。是否继续？`,
      '强制删除用户',
      {
        type: 'error',
        confirmButtonText: '清理并删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger'
      }
    )
  } catch {
    return
  }
  try {
    await adminApi.forceDeleteUser(row.id)
    toast.success('用户及关联记录已删除')
    await load()
  } catch {
    // 拦截器已提示
  }
}

async function openRoles(row: AdminUser) {
  await loadRoles()
  editing.value = row
  draftRoles.value = [...row.roleCodes]
  roleDialog.value = true
}

async function saveRoles() {
  if (!editing.value) return
  if (!draftRoles.value.length) {
    toast.warning('至少保留一个角色')
    return
  }
  saving.value = true
  try {
    await adminApi.updateUserRoles(editing.value.id, draftRoles.value)
    toast.success('角色已更新')
    roleDialog.value = false
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  load()
  loadRoles()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: var(--aies-gap);
}
.search {
  max-width: 260px;
}
.dim {
  font-size: 12px;
  color: var(--aies-ink3);
}
.mono {
  font-family: var(--aies-mono, monospace);
}
.ml {
  margin-left: 6px;
}
.mr {
  margin-right: 4px;
}
.warn {
  color: var(--aies-brand);
}
.role-check {
  display: flex;
  margin: 6px 0;
}
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
@media (max-width: 640px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
.field-hint {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: var(--aies-ink3);
}
.mb {
  margin-bottom: 10px;
}
</style>
