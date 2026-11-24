<template>
  <!-- 管理员专属：日志采集与审计窗口（运行日志 + 操作审计） -->
  <section class="log-center">
    <header class="head">
      <div>
        <h3>日志与审计</h3>
        <p class="hint">
          运行日志用于观察系统实时状态，审计记录用于追溯关键操作；窗口仅管理员可访问，所有数据只读。
        </p>
      </div>
    </header>

    <el-tabs v-model="activeTab" class="tabs">
      <el-tab-pane label="运行日志" name="runtime">
        <RuntimeLogPanel />
      </el-tab-pane>
      <el-tab-pane label="审计记录" name="audit" lazy>
        <AuditLogsPanel />
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import RuntimeLogPanel from '@/components/admin/RuntimeLogPanel.vue'
import AuditLogsPanel from '@/components/admin/AuditLogsPanel.vue'

const activeTab = ref('runtime')
</script>

<style scoped>
.head {
  margin-bottom: var(--aies-gap);
}
.head h3 {
  margin: 0 0 4px;
  font-size: 15px;
  color: var(--aies-ink);
}
.hint {
  margin: 0;
  max-width: 760px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--aies-ink3);
}
.tabs :deep(.el-tabs__header) {
  margin-bottom: var(--aies-gap);
}
</style>
