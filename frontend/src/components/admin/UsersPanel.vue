<template>
  <!-- 用户管理（user:manage）：检索/禁用启用/角色分配（UC-06/07/09） -->
  <section class="panel">
    <div class="toolbar">
      <el-input
        v-model="keyword"
        class="search"
        placeholder="按学号/工号或姓名筛选"
        clearable
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-select v-model="statusFilter" style="width: 130px" @change="reload">
        <el-option label="全部状态" value="" />
        <el-option label="正常" value="1" />
        <el-option label="已禁用" value="0" />
      </el-select>
      <el-select v-model="roleFilter" style="width: 140px" @change="reload">
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
              :title="code"
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
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
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
          </template>
        </el-table-column>
      </el-table>
    </DataTableShell>

    <el-dialog v-model="roleDialog" title="分配角色" width="440px">
      <el-alert
        v-if="editing"
        :closable="false"
        type="info"
        :title="`账号：${editing.username}（${editing.realName}）· 角色变更会立即刷新该用户权限缓存`"
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
import { onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { adminApi, type AdminRole, type AdminUser } from '@/api/admin'
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
const roleDialog = ref(false)
const editing = ref<AdminUser | null>(null)
const draftRoles = ref<string[]>([])

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
  if (row.roleCodes.includes('ADMIN')) return '管理员账号受保护（防锁死）'
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

async function toggleStatus(row: AdminUser) {
  const next = row.status === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(
      next === 0
        ? `禁用 ${row.username} 后：登录被拒（40103），刷新令牌立即作废；访问令牌最多 2 小时自然过期。`
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
    // 拦截器已提示后端 message
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
    toast.success('角色已更新，权限缓存已刷新')
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
.mb {
  margin-bottom: 10px;
}
</style>
