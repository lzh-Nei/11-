<template>
  <section class="panel">
    <el-alert
      type="info"
      show-icon
      :closable="false"
      class="tip"
      title="接入真实大模型：支持 OpenAI、DeepSeek、通义千问、Kimi、智谱及自定义 OpenAI 兼容接口；Ollama 本地模型无需 API Key。密钥加密保存，页面只显示尾号。"
    />

    <AppSkeleton v-if="loading" :rows="3" :row-height="56" />
    <ErrorState v-else-if="error" :message="error" @retry="load" />
    <EmptyState v-else-if="!config" description="暂无模型配置" />
    <div v-else class="cards">
      <section class="card form-card">
        <h3>模型接入配置</h3>
        <el-form label-position="top" class="form">
          <el-form-item label="模型供应商" required>
            <el-select v-model="form.provider" style="width: 100%" @change="onProviderChange">
              <el-option
                v-for="item in providers"
                :key="item.code"
                :label="item.label"
                :value="item.code"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="API Base URL">
            <el-input
              v-model="form.apiBaseUrl"
              :placeholder="providerMeta.basePlaceholder"
              maxlength="255"
              clearable
            />
            <div class="field-hint">{{ providerMeta.baseHint }}</div>
          </el-form-item>

          <el-form-item label="模型名" required>
            <el-input
              v-model="form.modelName"
              :placeholder="providerMeta.modelPlaceholder"
              maxlength="64"
            />
          </el-form-item>

          <el-form-item :label="providerMeta.keyLabel">
            <el-input
              v-model="form.apiKey"
              type="password"
              show-password
              autocomplete="new-password"
              :placeholder="keyPlaceholder"
              maxlength="512"
            />
            <div class="field-hint">
              {{
                config.apiKeyConfigured
                  ? `已配置（尾号 ${config.apiKeyHint || '****'}），留空保持不变`
                  : '尚未配置 API Key'
              }}
            </div>
          </el-form-item>

          <el-form-item label="评分随机度（固定，不可修改）">
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
          <el-form-item label="结果格式校验失败重试次数（0-5）" required>
            <el-input-number v-model="form.maxRetry" :min="0" :max="5" controls-position="right" />
          </el-form-item>

          <div class="actions">
            <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
            <el-button :loading="testing" @click="test">测试连接</el-button>
          </div>
          <p class="test-tip">测试连接使用已保存配置；修改后请先保存再测试。</p>

          <el-alert
            v-if="testResult"
            :type="testResult.success ? 'success' : 'error'"
            show-icon
            :closable="false"
            class="test-result"
            :title="
              testResult.success
                ? `连接成功：${testResult.modelVersion || '已返回'}，耗时 ${testResult.elapsedMs}ms`
                : `连接失败：${testResult.message}`
            "
          />
        </el-form>
      </section>

      <section class="card">
        <h3>当前设置</h3>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="供应商">{{ providerLabel(config.provider) }}</el-descriptions-item>
          <el-descriptions-item label="Base URL">
            <span class="mono">{{ config.apiBaseUrl || providerDefaultBase(config.provider) || '默认' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="模型">{{ config.modelName }}</el-descriptions-item>
          <el-descriptions-item label="API Key">
            {{ config.apiKeyConfigured ? `已配置（尾号 ${config.apiKeyHint || '****'}）` : '未配置' }}
          </el-descriptions-item>
          <el-descriptions-item label="评分随机度">{{ config.temperature }}</el-descriptions-item>
          <el-descriptions-item label="超时">{{ config.timeoutSeconds }} 秒</el-descriptions-item>
          <el-descriptions-item label="重试上限">{{ config.maxRetry }} 次</el-descriptions-item>
          <el-descriptions-item label="最近更新">{{
            formatDateTime(config.updateTime)
          }}</el-descriptions-item>
        </el-descriptions>
        <p class="dim">
          注：配置保存后在下一次 AI 评价时生效，不影响正在进行的评价；API Key
          只在服务端加密保存，不会通过接口回传。
        </p>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { adminApi, type AdminLlmConfig, type LlmTestResult } from '@/api/admin'
import { formatDateTime } from '@/utils/format'
import { toast } from '@/utils/toast'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'

interface ProviderOption {
  code: string
  label: string
  defaultBase: string
  basePlaceholder: string
  baseHint: string
  modelPlaceholder: string
  keyLabel: string
  requiresKey: boolean
}

const providers: ProviderOption[] = [
  {
    code: 'OLLAMA',
    label: 'Ollama 本地模型',
    defaultBase: '',
    basePlaceholder: '留空使用服务端 Ollama 地址',
    baseHint: '本地 Ollama 默认读取服务端 AI 配置，无需 API Key。',
    modelPlaceholder: '如 qwen2.5:7b',
    keyLabel: 'API Key（Ollama 无需填写）',
    requiresKey: false
  },
  {
    code: 'OPENAI',
    label: 'OpenAI',
    defaultBase: 'https://api.openai.com/v1',
    basePlaceholder: 'https://api.openai.com/v1',
    baseHint: '使用 OpenAI 官方或兼容代理地址。',
    modelPlaceholder: '如 gpt-4o-mini',
    keyLabel: 'API Key',
    requiresKey: true
  },
  {
    code: 'DEEPSEEK',
    label: 'DeepSeek',
    defaultBase: 'https://api.deepseek.com',
    basePlaceholder: 'https://api.deepseek.com',
    baseHint: 'DeepSeek 官方 OpenAI 兼容接口。',
    modelPlaceholder: '如 deepseek-chat',
    keyLabel: 'API Key',
    requiresKey: true
  },
  {
    code: 'DASHSCOPE',
    label: '通义千问（DashScope）',
    defaultBase: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    basePlaceholder: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
    baseHint: '阿里云百炼 DashScope 的 OpenAI 兼容模式。',
    modelPlaceholder: '如 qwen-plus',
    keyLabel: 'API Key',
    requiresKey: true
  },
  {
    code: 'MOONSHOT',
    label: 'Moonshot Kimi',
    defaultBase: 'https://api.moonshot.cn/v1',
    basePlaceholder: 'https://api.moonshot.cn/v1',
    baseHint: 'Moonshot 官方 OpenAI 兼容接口。',
    modelPlaceholder: '如 moonshot-v1-8k',
    keyLabel: 'API Key',
    requiresKey: true
  },
  {
    code: 'ZHIPU',
    label: '智谱 GLM',
    defaultBase: 'https://open.bigmodel.cn/api/paas/v4',
    basePlaceholder: 'https://open.bigmodel.cn/api/paas/v4',
    baseHint: '智谱开放平台的 OpenAI 兼容接口。',
    modelPlaceholder: '如 glm-4-flash',
    keyLabel: 'API Key',
    requiresKey: true
  },
  {
    code: 'CUSTOM',
    label: '自定义 OpenAI 兼容',
    defaultBase: '',
    basePlaceholder: 'https://your-provider.example.com/v1',
    baseHint: '填写兼容 /chat/completions 的服务地址。',
    modelPlaceholder: '填写服务商模型名',
    keyLabel: 'API Key',
    requiresKey: true
  }
]

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)
const error = ref<string | null>(null)
const config = ref<AdminLlmConfig | null>(null)
const testResult = ref<LlmTestResult | null>(null)

const form = reactive({
  provider: 'OLLAMA',
  apiBaseUrl: '',
  modelName: '',
  apiKey: '',
  timeoutSeconds: 60,
  maxRetry: 3
})

const providerMeta = computed(
  () => providers.find((item) => item.code === form.provider) ?? providers[0]
)
const keyPlaceholder = computed(() =>
  providerMeta.value.requiresKey
    ? config.value?.apiKeyConfigured
      ? '留空保持已配置密钥'
      : '请输入 API Key'
    : 'Ollama 本地模型无需 API Key'
)

function providerLabel(code: string): string {
  return providers.find((item) => item.code === code)?.label ?? code
}

function providerDefaultBase(code: string): string {
  return providers.find((item) => item.code === code)?.defaultBase ?? ''
}

function onProviderChange(code: string) {
  const meta = providers.find((item) => item.code === code)
  if (!meta) return
  form.apiBaseUrl = meta.defaultBase
  testResult.value = null
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const list = await adminApi.llmConfigs()
    config.value = list[0] ?? null
    if (config.value) {
      form.provider = config.value.provider || 'OLLAMA'
      form.apiBaseUrl = config.value.apiBaseUrl || ''
      form.modelName = config.value.modelName
      form.timeoutSeconds = config.value.timeoutSeconds
      form.maxRetry = config.value.maxRetry
      form.apiKey = ''
    }
  } catch (e) {
    error.value = (e as { message?: string })?.message || '模型配置加载失败，请稍后重试'
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
  const meta = providerMeta.value
  if (meta.requiresKey && !config.value.apiKeyConfigured && !form.apiKey.trim()) {
    toast.warning('该供应商需要 API Key，请填写后再保存')
    return
  }
  if (meta.requiresKey && !form.apiBaseUrl.trim()) {
    toast.warning('该供应商需要 Base URL')
    return
  }
  saving.value = true
  try {
    await adminApi.updateLlmConfig(config.value.id, {
      provider: form.provider,
      apiBaseUrl: form.apiBaseUrl.trim() || undefined,
      apiKey: form.apiKey.trim() || undefined,
      modelName: form.modelName.trim(),
      timeoutSeconds: form.timeoutSeconds,
      maxRetry: form.maxRetry
    })
    form.apiKey = ''
    testResult.value = null
    toast.success('模型配置已保存，下次评价调用生效')
    await load()
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function test() {
  if (!config.value) return
  testing.value = true
  testResult.value = null
  try {
    const result = await adminApi.testLlmConfig(config.value.id)
    testResult.value = result
    if (result.success) toast.success('模型连接成功')
    else toast.warning('模型连接失败，请检查配置')
  } catch (e) {
    toast.error((e as { message?: string })?.message || '连接测试失败', '请检查供应商配置')
  } finally {
    testing.value = false
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
  max-width: 420px;
}
.field-hint {
  margin-top: 4px;
  font-size: 11.5px;
  color: var(--aies-ink3);
  line-height: 1.6;
}
.dim {
  font-size: 12px;
  color: var(--aies-ink3);
  line-height: 1.7;
}
.actions {
  display: flex;
  gap: 10px;
  align-items: center;
}
.test-tip {
  margin: 8px 0 0;
  font-size: 11.5px;
  color: var(--aies-ink3);
}
.test-result {
  margin-top: 12px;
}
.mono {
  font-family: var(--aies-mono, monospace);
  font-size: 12.5px;
}
</style>
