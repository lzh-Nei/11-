<template>
  <!-- 维度雷达（SVG 自绘零依赖）：指标均分多边形，轴线 0-100 -->
  <svg :width="size" :height="size" :viewBox="`0 0 ${size} ${size}`" class="dim-radar">
    <!-- 网格环（25/50/75/100 四档） -->
    <polygon
      v-for="ring in rings"
      :key="ring"
      :points="ringPoints(ring)"
      fill="none"
      stroke="#e7e4de"
      stroke-width="1"
    />
    <!-- 轴线 + 轴标签 -->
    <line
      v-for="(v, i) in vertices(1)"
      :key="'ax' + i"
      :x1="cx"
      :y1="cy"
      :x2="v.x"
      :y2="v.y"
      stroke="#e7e4de"
    />
    <text
      v-for="(p, i) in labelPositions"
      :key="'lb' + i"
      :x="p.x"
      :y="p.y"
      :text-anchor="p.anchor"
      class="axis-label"
    >
      {{ names[i] }}
    </text>
    <!-- 数据多边形 -->
    <polygon
      :points="dataPoints"
      fill="rgba(180,83,9,0.14)"
      stroke="#b45309"
      stroke-width="2"
      stroke-linejoin="round"
    />
    <circle v-for="(v, i) in dataVertices" :key="'pt' + i" :cx="v.x" :cy="v.y" r="3.5" fill="#b45309" />
  </svg>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{ scores: Record<string, number>; size?: number; max?: number }>(), {
  size: 260,
  max: 100
})

const cx = computed(() => props.size / 2)
const cy = computed(() => props.size / 2 + 6)
const radius = computed(() => props.size / 2 - 34)
const names = computed(() => Object.keys(props.scores))

const rings = computed(() => [0.25, 0.5, 0.75, 1].map((r) => r * props.max))

function pointAt(i: number, ratio: number) {
  const n = names.value.length || 1
  const angle = (Math.PI * 2 * i) / n - Math.PI / 2
  return {
    x: cx.value + Math.cos(angle) * radius.value * ratio,
    y: cy.value + Math.sin(angle) * radius.value * ratio
  }
}

function vertices(ratio: number) {
  const n = names.value.length || 1
  return Array.from({ length: n }, (_, i) => pointAt(i, ratio))
}

function ringPoints(ring: number) {
  return vertices(ring / props.max)
    .map((v) => `${v.x.toFixed(1)},${v.y.toFixed(1)}`)
    .join(' ')
}

const dataVertices = computed(() =>
  names.value.map((name, i) =>
    pointAt(i, Math.min(Math.max(Number(props.scores[name] ?? 0) / props.max, 0), 1))
  )
)

const dataPoints = computed(() =>
  dataVertices.value.map((v) => `${v.x.toFixed(1)},${v.y.toFixed(1)}`).join(' ')
)

const labelPositions = computed(() =>
  names.value.map((name, i) => {
    const n = names.value.length || 1
    const angle = (Math.PI * 2 * i) / n - Math.PI / 2
    const r = radius.value + 16
    const anchor = Math.abs(Math.cos(angle)) < 0.3 ? 'middle' : Math.cos(angle) > 0 ? 'start' : 'end'
    // 标签约束在 viewBox 内：CJK 按 12px/字估算，避免小尺寸下被 SVG 边界裁切
    const w = Math.max(String(name).length, 2) * 12
    let x = cx.value + Math.cos(angle) * r
    if (anchor === 'middle') {
      x = Math.min(Math.max(x, w / 2 + 2), props.size - w / 2 - 2)
    } else if (anchor === 'start') {
      x = Math.min(x, props.size - w - 2)
    } else {
      x = Math.max(x, w + 2)
    }
    return { x, y: cy.value + Math.sin(angle) * r + 4, anchor }
  })
)
</script>

<style scoped>
.axis-label {
  font-size: 12px;
  fill: #57534e;
}
</style>
