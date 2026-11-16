<template>
  <!-- LLM 参数（llm:config，UC-33/37）：运行参数读写 llm_config（R4 红线）；
       temperature 锁定 0 只读，变更在下一次评价调用生效 -->
  <section class="panel">
    <el-alert
      type="info"
      show-icon
      :closable="false"
      class="tip"
      title="运行参数来自 llm_config 表（R4：禁止硬编码）；temperature 锁定 0 以保证评分复现性，仅模型名/超时/重试次数可调。"
    />

    <AppSkeleton v-if="loading" :rows="3" :row-height="56" />
    <ErrorState v-else-if="error" :message="error" @retry="load" />
    <EmptyState v-else-if="!config" description="尚无 LLM 配置行（llm_config 为空，请先执行数据初始化）" />
    <div v-else class="cards">
      <section class="card form-card">
        <h3>运行参数</h3>
        <el-form label-position="top" class="form">
          <el-form-item label="模型名" required>
            <el-input v-model="form.modelName" placeholder="如：qwen2.5:7b" maxlength="64" />
          </el-form-item>
          <el-form-item label="temperature（锁定，不可修改）">
            <el-input :model-value="String(config.temperature)" disabled />
          </el-form-item>
          <el-form-item label="调用超时（秒，10-300）" required>
            <el-input-number
              v-model="form.timeoutSeconds"
              :min="10"
              :max="300"
              :step="10"
              controls-position="right"
            />
          </el-form-item>
          <el-form-item label="Schema 校验失败重试上限（0-5）" required>
            <el-input-number v-model="form.maxRetry" :min="0" :max="5" controls-position="right" />
          </el-form-item>
          <el-button type="primary" :loading="saving" @click="save">保存运行参数</el-button>
        </el-form>
      </section>

      <section class="card">
        <h3>当前快照</h3>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="配置行">#{{ config.id }}</el-descriptions-item>
          <el-descriptions-item label="模型">{{ config.modelName }}</el-descriptions-item>
          <el-descriptions-item label="temperature">{{ config.temperature }}</el-descriptions-item>
          <el-descriptions-item label="超时">{{ config.timeoutSeconds }} 秒</el-descriptions-item>
          <el-descriptions-item label="重试上限">{{ config.maxRetry }} 次</el-descriptions-item>
          <el-descriptions-item label="最近更新">{{
            formatDateTime(config.updateTime)
          }}</el-descriptions-item>
        </el-descriptions>
        <p class="dim">
          注：模型名需与 Ollama 已拉取模型一致；参数保存后在下一次 AI 评价调用时读取生效，
          不影响进行中的排队任务。
        </p>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { adminApi, type AdminLlmConfig } from '@/api/admin'
import { formatDateTime } from '@/utils/format'
import { toast } from '@/utils/toast'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)
const config = ref<AdminLlmConfig | null>(null)
const form = reactive({ modelName: '', timeoutSeconds: 60, maxRetry: 3 })

async function load() {
  loading.value = true
  error.value = null
  try {
    const list = await adminApi.llmConfigs()
    config.value = list[0] ?? null
    if (config.value) {
      form.modelName = config.value.modelName
      form.timeoutSeconds = config.value.timeoutSeconds
      form.maxRetry = config.value.maxRetry
    }
  } catch (e) {
    error.value = (e as { message?: string })?.message || 'LLM 配置加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!config.value) return
  if (!form.modelName.trim()) {
    toast.warning('请填写模型名')
    return
  }
  saving.value = true
  try {
    await adminApi.updateLlmConfig(config.value.id, {
      modelName: form.modelName.trim(),
      timeoutSeconds: form.timeoutSeconds,
      maxRetry: form.maxRetry
    })
    toast.success('运行参数已保存，下次评价调用生效')
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
.tip {
  margin-bottom: var(--aies-gap);
}
.cards {
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: var(--aies-gap);
}
@media (max-width: 960px) {
  .cards {
    grid-template-columns: 1fr;
  }
}
.card {
  background: var(--aies-paper);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  padding: var(--aies-gap-lg);
}
.card h3 {
  margin: 0 0 12px;
  font-size: 14.5px;
  color: var(--aies-ink);
}
.form {
  max-width: 360px;
}
.dim {
  font-size: 12px;
  color: var(--aies-ink3);
  line-height: 1.7;
}
</style>
