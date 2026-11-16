<template>
  <!-- 个人中心：基础信息展示 + 修改用户信息（自助，姓名/手机号）+ 安全操作 -->
  <PageShell>
    <PageHeader
      title="个人中心"
      subtitle="账号基础信息展示与自助资料维护（GET /api/auth/me · PUT /api/profile）"
    >
      <el-button size="small" :loading="reloading" @click="reloadMe">刷新用户信息</el-button>
    </PageHeader>

    <div class="top-grid">
      <!-- 模块一：基础信息展示（只读档案） -->
      <section class="card">
        <h3>基础信息展示</h3>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="学号/工号">{{ auth.me?.username ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ auth.me?.realName ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="角色">
            <el-tag
              v-for="r in auth.me?.roles ?? []"
              :key="r"
              class="tag-gap"
              effect="dark"
              :type="roleTagType"
              :title="r"
            >
              {{ roleName(r) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ auth.me?.email || '未绑定' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ maskPhone(auth.me?.phone) }}</el-descriptions-item>
          <el-descriptions-item label="已授予权限">
            {{ auth.me?.permissions?.length ?? 0 }} 项
          </el-descriptions-item>
        </el-descriptions>
        <p class="hint">学号/工号、角色与邮箱由账号体系维护；如需变更请联系管理员。</p>
      </section>

      <!-- 模块二：修改用户信息（自助） -->
      <section class="card">
        <h3>修改用户信息</h3>
        <el-form label-position="top" class="edit-form">
          <el-form-item label="学号 / 工号（不可修改）">
            <el-input :model-value="auth.me?.username ?? ''" disabled />
          </el-form-item>
          <el-form-item label="姓名" required>
            <el-input v-model="form.realName" placeholder="请输入姓名" maxlength="32" show-word-limit />
          </el-form-item>
          <el-form-item label="手机号（选填，留空表示清除）">
            <el-input v-model="form.phone" placeholder="11 位手机号" maxlength="11" />
          </el-form-item>
          <el-form-item label="邮箱（不可自助修改）">
            <el-input :model-value="auth.me?.email || '未绑定'" disabled />
          </el-form-item>
          <p v-if="errorTip" class="error-tip">{{ errorTip }}</p>
          <div class="actions">
            <el-button type="primary" :loading="saving" :disabled="!canSave" @click="save">
              保存资料
            </el-button>
            <el-button :disabled="!dirty || saving" @click="resetForm">重置</el-button>
          </div>
        </el-form>
      </section>
    </div>

    <!-- 模块三：安全操作 -->
    <section class="card security-card">
      <h3>安全操作</h3>
      <div class="security-list">
        <div class="security-item">
          <div>
            <b>修改密码</b>
            <small>邮箱验证码校验；成功后需重新登录（作废全部刷新令牌）</small>
          </div>
          <el-button size="small" @click="router.push('/change-password')">去修改</el-button>
        </div>
        <div class="security-item">
          <div>
            <b>退出登录</b>
            <small>当前访问令牌立即加入黑名单</small>
          </div>
          <el-button size="small" plain @click="onLogout">退出</el-button>
        </div>
      </div>
      <p v-if="auth.firstLogin" class="first-tip">首次登录：建议先修改初始密码（UC-07）。</p>
    </section>
  </PageShell>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { profileApi } from '@/api/profile'
import PageShell from '@/components/ui/PageShell.vue'
import PageHeader from '@/components/ui/PageHeader.vue'
import { toast } from '@/utils/toast'

const router = useRouter()
const auth = useAuthStore()
const reloading = ref(false)
const saving = ref(false)
const form = reactive({ realName: '', phone: '' })

const roleTagType = computed(() => {
  if (auth.role === 'ADMIN') return 'danger'
  if (auth.role === 'TEACHER') return 'warning'
  return 'success'
})

const phoneValid = computed(() => !form.phone.trim() || /^1[3-9]\d{9}$/.test(form.phone.trim()))
const dirty = computed(
  () => form.realName.trim() !== (auth.me?.realName ?? '') || form.phone.trim() !== (auth.me?.phone ?? '')
)
const canSave = computed(() => form.realName.trim().length > 0 && phoneValid.value && dirty.value)
const errorTip = computed(() => {
  if (form.realName.trim().length === 0) return '姓名不能为空'
  if (!phoneValid.value) return '手机号格式不正确（11 位，1 开头）'
  if (!dirty.value) return '资料未发生变化'
  return ''
})

function roleName(code: string): string {
  if (code === 'ADMIN') return '管理员'
  if (code === 'TEACHER') return '教师'
  if (code === 'STUDENT') return '学生'
  return code
}

function maskPhone(phone: string | null | undefined): string {
  if (!phone || phone.length < 7) return '—'
  return `${phone.slice(0, 3)}****${phone.slice(-4)}`
}

function resetForm() {
  form.realName = auth.me?.realName ?? ''
  form.phone = auth.me?.phone ?? ''
}

watch(
  () => auth.me,
  () => resetForm(),
  { immediate: true }
)

async function reloadMe() {
  reloading.value = true
  try {
    await auth.loadMe()
    toast.success('用户信息已刷新')
  } catch {
    toast.error('用户信息刷新失败', '请稍后重试')
  } finally {
    reloading.value = false
  }
}

async function save() {
  if (!canSave.value) return
  saving.value = true
  try {
    const updated = await profileApi.update({
      realName: form.realName.trim(),
      phone: form.phone.trim()
    })
    auth.setMe(updated)
    resetForm()
    toast.success('个人资料已更新')
  } catch {
    // 后端校验/网络错误由拦截器统一提示
  } finally {
    saving.value = false
  }
}

async function onLogout() {
  await auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.top-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--aies-gap);
}
@media (max-width: 1024px) {
  .top-grid {
    grid-template-columns: 1fr;
  }
}
.card {
  background: var(--aies-surface);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  box-shadow: var(--aies-shadow);
  padding: var(--aies-gap-lg);
}
.card h3 {
  margin: 0 0 12px;
  font-size: 15px;
  color: var(--aies-ink);
}
.tag-gap {
  margin-right: 6px;
}
.hint {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--aies-ink3);
  line-height: 1.7;
}
.edit-form {
  max-width: 420px;
}
.error-tip {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--aies-brand);
}
.actions {
  display: flex;
  gap: 8px;
}
.security-card {
  margin-top: var(--aies-gap);
}
.security-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
@media (max-width: 800px) {
  .security-list {
    grid-template-columns: 1fr;
  }
}
.security-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius);
  background: var(--aies-paper);
}
.security-item b {
  display: block;
  font-size: 13.5px;
  color: var(--aies-ink);
}
.security-item small {
  display: block;
  margin-top: 2px;
  font-size: 11.5px;
  color: var(--aies-ink3);
  line-height: 1.5;
}
.first-tip {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--aies-brand);
}
</style>
