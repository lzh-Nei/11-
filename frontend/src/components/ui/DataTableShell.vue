<template>
  <!-- 表格壳：四态齐全（骨架/空/错误/数据）+ 分页 + 行点击进详情（列表铁律） -->
  <div class="data-table-shell">
    <AppSkeleton v-if="loading" :rows="4" />
    <ErrorState v-else-if="error" :message="error" @retry="emit('retry')" />
    <EmptyState
      v-else-if="!rows.length"
      :description="emptyText"
      :action-text="emptyActionText"
      @action="emit('emptyAction')"
    />
    <template v-else>
      <div class="table-card">
        <slot />
      </div>
      <div v-if="total > pageSize" class="pager">
        <el-pagination
          layout="prev, pager, next, total"
          :total="total"
          :page-size="pageSize"
          :current-page="page"
          @current-change="(p: number) => emit('pageChange', p)"
        />
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import AppSkeleton from './AppSkeleton.vue'
import ErrorState from './ErrorState.vue'
import EmptyState from './EmptyState.vue'

withDefaults(
  defineProps<{
    loading?: boolean
    error?: string | null
    rows: unknown[]
    total?: number
    page?: number
    pageSize?: number
    emptyText?: string
    emptyActionText?: string
  }>(),
  { loading: false, error: null, total: 0, page: 1, pageSize: 10, emptyText: '暂无数据' }
)

const emit = defineEmits<{ retry: []; pageChange: [p: number]; emptyAction: [] }>()
</script>

<style scoped>
.table-card {
  background: var(--aies-surface);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  box-shadow: var(--aies-shadow);
  overflow: hidden;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--aies-gap);
}
</style>
