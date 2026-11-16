<template>
  <div class="style-guide">
    <!-- 样板页声明横幅：全站唯一允许 mock 的页面（工单条款） -->
    <div class="mock-banner">
      样板演示 · 全站未来观感预览（本页数据为 mock，全站唯一允许 mock 处；真实页面一律接真接口）
    </div>

    <PageHeader
      title="AIES 设计系统样板页"
      subtitle="风格基因：琥珀主色 · 暖纸底白卡 · 圆角 8/12 · 双层柔和阴影 · 220ms 缓动 —— 批0 风格裁决门"
    >
      <el-tag type="warning" effect="plain">WP8-B0 裁决门</el-tag>
    </PageHeader>

    <!-- ① 数据态全景：统计卡行 -->
    <section class="sg-block">
      <h2>① 统计卡（StatCard）</h2>
      <div class="stat-grid">
        <StatCard label="任务总数" :value="4" footnote="含 2 个已发布" />
        <StatCard label="提交总数" :value="6" unit="份" footnote="今日新增 2" />
        <StatCard label="已出分" :value="4" footnote="AI 评价 3 · 教师复核 1" />
        <StatCard label="待批阅" :value="2" footnote="预计 5 分钟内出分" />
      </div>
    </section>

    <!-- ② 四态实景 -->
    <section class="sg-block">
      <h2>② 四态齐全（加载 / 空 / 错误 / 数据）</h2>
      <div class="two-col">
        <div class="sg-cell">
          <span class="cell-tag">加载态 AppSkeleton</span>
          <AppSkeleton :rows="3" :row-height="44" />
        </div>
        <div class="sg-cell">
          <span class="cell-tag">空态 EmptyState（说明+引导）</span>
          <EmptyState description="还没有任何提交记录" action-text="去提交作业" @action="noop" />
        </div>
        <div class="sg-cell">
          <span class="cell-tag">错误态 ErrorState（卡+重试，禁裸错误码）</span>
          <ErrorState message="服务暂时不可用（HTTP 503）" @retry="noop" />
        </div>
        <div class="sg-cell">
          <span class="cell-tag">确认弹窗 ConfirmAction（不可逆操作必确认）</span>
          <div class="demo-actions">
            <ConfirmAction
              title="发布任务"
              message="发布后指标与截止时间将锁定，学生立即可见。此操作不可逆，确认发布？"
              confirm-text="确认发布"
              @execute="demoExecute"
            >
              <template #default="{ trigger, loading }">
                <el-button type="primary" :loading="loading" @click="trigger">发布任务（演示）</el-button>
              </template>
            </ConfirmAction>
            <el-button @click="toast.success('操作成功：这就是成功 toast 的统一口径')">成功 toast</el-button>
            <el-button @click="toast.error('复核提交失败', '请检查网络后重试')">失败 toast</el-button>
          </div>
        </div>
      </div>
    </section>

    <!-- ③ 格式化死刑条款实景对照 -->
    <section class="sg-block">
      <h2>③ 格式化规则（API 字母 → 人类语言，禁裸数据铁律）</h2>
      <div class="table-card">
        <table>
          <thead>
            <tr><th>原始 API 数据</th><th>禁止显示</th><th>必须渲染为</th></tr>
          </thead>
          <tbody class="mono-src">
            <tr>
              <td>2026-11-16T12:03:44Z</td>
              <td class="bad">ISO 原串</td>
              <td><b>{{ formatDateTime('2026-11-16T12:03:44Z') }}</b>（列表态：<i>{{ formatRelative('2026-11-16T12:03:44Z') }}</i>）</td>
            </tr>
            <tr>
              <td>"PENDING" / "AI_DONE" / "REVIEWED"</td>
              <td class="bad">大写英文</td>
              <td><StatusTag status="PENDING" /> <StatusTag status="AI_DONE" /> <StatusTag status="REVIEWED" /> <StatusTag status="FAILED" /> <StatusTag status="DRAFT" /> <StatusTag status="PUBLISHED" /></td>
            </tr>
            <tr>
              <td>87.5</td>
              <td class="bad">裸分数</td>
              <td style="display:flex;gap:18px;align-items:center">
                <ScoreDisplay :score="92" size="mini" />
                <ScoreDisplay :score="74" size="mini" />
                <ScoreDisplay :score="58" size="mini" />
                <ScoreDisplay :score="87.5" />
              </td>
            </tr>
            <tr>
              <td>3f9c1a2e-88d4-4b6a-9e21-7c0d5b1f2a33</td>
              <td class="bad">uuid 整串裸显</td>
              <td>
                <span class="mono-src">{{ maskId('3f9c1a2e-88d4-4b6a-9e21-7c0d5b1f2a33') }}</span>
                <el-button size="small" text type="primary" @click="copyTrace">详情折叠 · 一键复制</el-button>
              </td>
            </tr>
            <tr>
              <td>1048576</td>
              <td class="bad">裸字节数</td>
              <td><b>{{ formatBytes(1048576) }}</b>（832 KB 文件 → {{ formatBytes(851968) }}）</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- ④ 表格壳 + 上传区 + 图表容器 -->
    <section class="sg-block">
      <h2>④ 业务容器（DataTableShell / FileUploadZone / ChartPanel）</h2>
      <div class="stack">
        <DataTableShell :rows="[{ a: 1 }, { a: 2 }, { a: 3 }]" :total="42" :page="1" :page-size="10" empty-text="暂无提交">
          <table class="demo-table">
            <thead>
              <tr><th>任务</th><th>学生</th><th>状态</th><th>提交时间</th><th>得分</th></tr>
            </thead>
            <tbody>
              <tr v-for="r in demoRows" :key="r.id" class="row-click" @click="noop">
                <td>{{ r.task }}</td>
                <td>{{ r.student }}</td>
                <td><StatusTag :status="r.status" /></td>
                <td>{{ formatDateTime(r.at) }}</td>
                <td><ScoreDisplay v-if="r.score != null" :score="r.score" size="mini" /><i v-else class="dim">—</i></td>
              </tr>
            </tbody>
          </table>
        </DataTableShell>
        <div class="two-col">
          <div class="sg-cell">
            <span class="cell-tag">上传区 FileUploadZone（分片×5 收敛 · 进度/速度/重试）</span>
            <FileUploadZone
              uploading
              file-name="数据结构实验报告-v3.docx"
              :percent="64"
              :sent-bytes="671089"
              :total-bytes="1048576"
              :bytes-per-second="210000"
            />
          </div>
          <div class="sg-cell">
            <span class="cell-tag">图表容器 ChartPanel（CSS 示意柱，实图批2接真数据）</span>
            <ChartPanel title="均分分布" hint="mock 示意">
              <div class="demo-bars">
                <div v-for="b in bars" :key="b.label" class="bar-col">
                  <div class="bar" :style="{ height: b.h + '%', background: b.c }" />
                  <span>{{ b.label }}</span>
                </div>
              </div>
            </ChartPanel>
          </div>
        </div>
      </div>
    </section>

    <footer class="sg-footer">
      批0 组件清单（12）：PageHeader · StatCard · AppSkeleton · EmptyState · ErrorState · StatusTag ·
      ScoreDisplay · ConfirmAction · DataTableShell · ChartPanel · FileUploadZone · CaptchaInput
      ＋工具层：format.ts · status.ts · toast.ts —— 后续批次按落户总表逐页落位
    </footer>
  </div>
</template>

<script setup lang="ts">
import PageHeader from '@/components/ui/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import AppSkeleton from '@/components/ui/AppSkeleton.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import StatusTag from '@/components/ui/StatusTag.vue'
import ScoreDisplay from '@/components/ui/ScoreDisplay.vue'
import ConfirmAction from '@/components/ui/ConfirmAction.vue'
import DataTableShell from '@/components/ui/DataTableShell.vue'
import ChartPanel from '@/components/ui/ChartPanel.vue'
import FileUploadZone from '@/components/ui/FileUploadZone.vue'
import { toast } from '@/utils/toast'
import { formatDateTime, formatRelative, formatBytes, maskId } from '@/utils/format'

/* 样板演示数据（全站唯一 mock 处，横幅已标注） */
const demoRows = [
  { id: 1, task: '实训作业1 · Word 文档格式检查', student: 'S2024001 陈同学', status: 'REVIEWED', at: '2026-11-16T12:03:44Z', score: 92 },
  { id: 2, task: 'C2任务2 · 代码结构分析', student: 'S2024002 林同学', status: 'AI_DONE', at: '2026-11-16T09:41:00Z', score: 74 },
  { id: 3, task: '算法作业3 · 动态规划', student: 'S2024003 王同学', status: 'PENDING', at: '2026-11-15T18:20:00Z', score: null },
  { id: 4, task: '实训作业1 · Word 文档格式检查', student: 'S2024004 赵同学', status: 'FAILED', at: '2026-11-15T14:02:00Z', score: null }
]
const bars = [
  { label: '0-59', h: 22, c: '#b91c1c' },
  { label: '60-69', h: 48, c: '#b45309' },
  { label: '70-79', h: 66, c: '#d97706' },
  { label: '80-89', h: 84, c: '#0f766e' },
  { label: '90-100', h: 58, c: '#15803d' }
]

function noop() {}
function demoExecute(done: () => void) {
  setTimeout(done, 600)
}
function copyTrace() {
  navigator.clipboard?.writeText('3f9c1a2e-88d4-4b6a-9e21-7c0d5b1f2a33')
  toast.success('trace_id 已复制')
}
</script>

<style scoped>
.style-guide {
  max-width: 1180px;
  margin: 0 auto;
  padding: var(--aies-gap-lg) var(--aies-gap-lg) 80px;
}
.mock-banner {
  background: #fef3c7;
  color: #78350f;
  border: 1px solid #f0e3bb;
  border-radius: var(--aies-radius);
  padding: 10px 16px;
  font-size: 13px;
  font-weight: 600;
  margin-bottom: var(--aies-gap-lg);
}
.sg-block {
  margin-bottom: var(--aies-gap-lg) + var(--aies-gap);
}
.sg-block > h2 {
  font-size: 16px;
  color: var(--aies-ink);
  margin: 0 0 var(--aies-gap);
  padding-left: 10px;
  border-left: 3px solid var(--aies-brand);
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--aies-gap);
}
.two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--aies-gap);
}
@media (max-width: 900px) {
  .two-col {
    grid-template-columns: 1fr;
  }
}
.sg-cell {
  min-width: 0;
}
.cell-tag {
  display: inline-block;
  font-size: 12px;
  font-weight: 600;
  color: var(--aies-ink3);
  margin-bottom: 8px;
}
.demo-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  align-items: center;
  background: var(--aies-surface);
  border: 1px dashed var(--aies-border);
  border-radius: var(--aies-radius-lg);
  padding: 16px var(--aies-gap-lg);
}
.table-card {
  background: var(--aies-surface);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  box-shadow: var(--aies-shadow);
  overflow: auto;
}
table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
th {
  text-align: left;
  padding: 10px 14px;
  background: var(--aies-paper);
  color: var(--aies-ink3);
  font-size: 12px;
}
td {
  padding: 10px 14px;
  border-top: 1px solid var(--aies-border);
  color: var(--aies-ink);
}
.mono-src {
  font-family: var(--aies-mono, monospace);
  font-size: 12.5px;
}
.bad {
  color: #b91c1c;
  text-decoration: line-through;
}
.demo-table .row-click {
  cursor: pointer;
  transition: background var(--aies-t);
}
.demo-table .row-click:hover {
  background: var(--aies-paper);
}
.dim {
  color: var(--aies-ink3);
}
.stack {
  display: flex;
  flex-direction: column;
  gap: var(--aies-gap);
}
.demo-bars {
  display: flex;
  align-items: flex-end;
  gap: 18px;
  height: 180px;
  padding: 8px 6px 0;
}
.bar-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  height: 100%;
  justify-content: flex-end;
}
.bar {
  width: 70%;
  border-radius: 6px 6px 0 0;
}
.bar-col span {
  font-size: 12px;
  color: var(--aies-ink3);
  font-family: var(--aies-mono, monospace);
}
.sg-footer {
  margin-top: var(--aies-gap-lg);
  padding: 14px 16px;
  background: var(--aies-surface);
  border: 1px solid var(--aies-border);
  border-radius: var(--aies-radius-lg);
  font-size: 12.5px;
  color: var(--aies-ink3);
  line-height: 1.7;
}
</style>
