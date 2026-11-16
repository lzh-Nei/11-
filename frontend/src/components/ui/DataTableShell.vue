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
      <div class="table-card" :style="{ '--table-scroll-max': scrollMaxHeight }">
        <ScrollArea :max-height="scrollMaxHeight">
          <slot />
        </ScrollArea>
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
import ScrollArea from './ScrollArea.vue'

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
    scrollMaxHeight?: string
  }>(),
  {
    loading: false,
    error: null,
    total: 0,
    page: 1,
    pageSize: 10,
    emptyText: '暂无数据',
    scrollMaxHeight: '62vh'
  }
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
/* Element Plus 表格：让身体区在表头下方内部纵向滚动（保留横向滚动与固定列），
   外层 ScrollArea 仅兜底自定义原生表格；-56px 预留表头与横向滚动条高度 */
:deep(.el-scrollbar__wrap) {
  max-height: calc(var(--table-scroll-max, 62vh) - 56px);
  overflow-y: auto;
  overscroll-behavior: auto;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--aies-gap);
}
</style>
