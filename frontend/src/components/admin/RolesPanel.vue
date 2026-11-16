<template>
  <!-- 角色权限管理（role:manage，UC-31/32）：角色矩阵维护；ADMIN 内置角色只读防锁死 -->
  <section class="panel">
    <div class="role-layout">
      <aside class="role-list">
        <button
          v-for="r in roles"
          :key="r.id"
          type="button"
          class="role-item"
          :class="{ active: r.id === selectedId }"
          @click="selectRole(r)"
        >
          <span class="role-code">{{ roleLabel(r.roleCode) }}</span>
          <small>{{ r.userCount }} 个账号</small>
          <el-tag v-if="r.roleCode === 'ADMIN'" size="small" type="danger">只读</el-tag>
        </button>
      </aside>

      <div class="matrix">
        <el-alert
          v-if="selected?.roleCode === 'ADMIN'"
          type="warning"
          show-icon
          :closable="false"
          title="管理员角色的权限为系统保护项，不能在页面修改。"
          class="mb"
        />
        <div class="matrix-head">
          <h3>{{ selected ? `${roleLabel(selected.roleCode)} · 权限设置` : '选择角色' }}</h3>
          <div class="actions">
            <el-button size="small" :disabled="!dirty" @click="resetDraft">重置</el-button>
            <el-button
              size="small"
              type="primary"
              :disabled="!dirty || selected?.roleCode === 'ADMIN'"
              :loading="saving"
              @click="save"
            >
              保存权限
            </el-button>
          </div>
        </div>

        <AppSkeleton v-if="loading" :rows="4" />
        <ErrorState v-else-if="error" :message="error" @retry="load" />
        <ScrollArea v-else max-height="calc(100vh - 380px)" padded>
          <div class="groups">
            <div v-for="group in grouped" :key="group.module" class="group">
              <div class="group-head">
                <el-checkbox
                  :model-value="allChecked(group.codes)"
                  :indeterminate="someChecked(group.codes)"
                  :disabled="readonly"
                  @change="(v: boolean) => toggleGroup(group.codes, v)"
                >
                  <b>{{ permModuleLabel(group.module) }}</b>
                </el-checkbox>
                <span class="dim">{{ group.codes.length }} 项</span>
              </div>
              <el-checkbox-group v-model="draftPerms" :disabled="readonly">
                <el-checkbox
                  v-for="p in group.items"
                  :key="p.permCode"
                  :value="p.permCode"
                  class="perm-check"
                >
                  {{ p.permName }}
                </el-checkbox>
              </el-checkbox-group>
            </div>
          </div>
        </ScrollArea>
        <p v-if="dirty" class="dirty-hint">有未保存的变更（保存后立即生效）</p>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { adminApi, type AdminPermission, type AdminRole } from '@/api/admin'
import { permModuleLabel, roleLabel } from '@/constants/admin'
import { toast } from '@/utils/toast'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import ScrollArea from '@/components/ui/ScrollArea.vue'

const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
const roles = ref<AdminRole[]>([])
const permissions = ref<AdminPermission[]>([])
const selectedId = ref<number | null>(null)
const draftPerms = ref<string[]>([])

const selected = computed(() => roles.value.find((r) => r.id === selectedId.value) ?? null)
const readonly = computed(() => selected.value?.roleCode === 'ADMIN')

const grouped = computed(() => {
  const ORDER = ['auth', 'user', 'task', 'submission', 'evaluation', 'report', 'system']
  const map = new Map<string, AdminPermission[]>()
  for (const p of permissions.value) {
    const list = map.get(p.module) ?? []
    list.push(p)
    map.set(p.module, list)
  }
  return Array.from(map.entries())
    .sort(([a], [b]) => {
      const ai = ORDER.indexOf(a)
      const bi = ORDER.indexOf(b)
      return (ai === -1 ? 99 : ai) - (bi === -1 ? 99 : bi)
    })
    .map(([module, items]) => ({
      module,
      items,
      codes: items.map((p) => p.permCode)
    }))
})

const dirty = computed(() => {
  if (!selected.value) return false
  const before = [...selected.value.permCodes].sort().join(',')
  const after = [...draftPerms.value].sort().join(',')
  return before !== after
})

function selectRole(role: AdminRole) {
  selectedId.value = role.id
  draftPerms.value = [...role.permCodes]
}

function allChecked(codes: string[]): boolean {
  const set = new Set(draftPerms.value)
  return codes.length > 0 && codes.every((c) => set.has(c))
}

function someChecked(codes: string[]): boolean {
  const set = new Set(draftPerms.value)
  const count = codes.filter((c) => set.has(c)).length
  return count > 0 && count < codes.length
}

function toggleGroup(codes: string[], checked: boolean) {
  const set = new Set(draftPerms.value)
  for (const code of codes) {
    if (checked) set.add(code)
    else set.delete(code)
  }
  draftPerms.value = Array.from(set)
}

function resetDraft() {
  if (selected.value) draftPerms.value = [...selected.value.permCodes]
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const [roleList, permList] = await Promise.all([adminApi.roles(), adminApi.permissions()])
    roles.value = roleList
    permissions.value = permList
    if (selectedId.value === null && roleList.length) {
      selectRole(roleList[0])
    } else {
      const current = roleList.find((r) => r.id === selectedId.value)
      if (current) selectRole(current)
    }
  } catch (e) {
    error.value = (e as { message?: string })?.message || '角色权限加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!selected.value) return
  try {
    await ElMessageBox.confirm(
      `保存后，${roleLabel(selected.value.roleCode)}角色的用户权限将立即更新。`,
      '保存权限设置',
      { type: 'warning', confirmButtonText: '确认保存', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  saving.value = true
  try {
    await adminApi.updateRolePermissions(selected.value.id, draftPerms.value)
    toast.success('权限设置已保存')
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.role-layout {
  display: grid;
  grid-template-columns: 200px 1fr;
  gap: var(--aies-gap);
}
@media (max-width: 960px) {
  .role-layout {
    grid-template-columns: 1fr;
  }
}
.role-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.role-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 12px 14px;
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius);
  background: var(--aies-paper);
  cursor: pointer;
  font-family: inherit;
  text-align: left;
  position: relative;
  transition:
    border-color var(--aies-t),
    background var(--aies-t);
}
.role-item:hover {
  border-color: var(--aies-brand);
}
.role-item.active {
  border-color: var(--aies-brand);
  background: var(--aies-brand-soft);
}
.role-code {
  font-size: 14px;
  font-weight: 600;
  color: var(--aies-ink);
}
.role-item small {
  font-size: 11.5px;
  color: var(--aies-ink3);
}
.role-item .el-tag {
  position: absolute;
  top: 10px;
  right: 10px;
}
.matrix {
  min-width: 0;
}
.matrix-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.matrix-head h3 {
  margin: 0;
  font-size: 15px;
  color: var(--aies-ink);
}
.actions {
  display: flex;
  gap: 8px;
}
.groups {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.group {
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius);
  padding: 10px 14px;
  background: var(--aies-paper);
}
.group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.perm-check {
  display: flex;
  margin: 4px 0;
}
.perm-check code {
  margin-left: 6px;
  font-family: var(--aies-mono, monospace);
  font-size: 10.5px;
  color: var(--aies-ink3);
}
.dim {
  font-size: 11.5px;
  color: var(--aies-ink3);
}
.dirty-hint {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--aies-brand);
}
.mb {
  margin-bottom: 12px;
}
</style>
