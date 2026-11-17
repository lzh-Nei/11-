import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
/* D-39-WP4：主题层必须在 EP css 之后引入（后到者赢），覆盖 --el-* 为品牌琥珀 */
import './style.css'
import App from './App.vue'
import {
  clearLegacyTokens,
  receiveAuthHandoff,
  startAuthHandoffResponder
} from '@/utils/authSession'

async function bootstrap() {
  // 旧版本 localStorage token 不再复用；新标签页先完成一次性登录态交接再挂载路由
  clearLegacyTokens()
  startAuthHandoffResponder()
  await receiveAuthHandoff()
  // 路由模块在交接清理 URL 后再加载，避免 createWebHistory 捕获带 handoff 的初始地址
  const { default: router } = await import('./router')
  createApp(App).use(createPinia()).use(router).use(ElementPlus, { locale: zhCn }).mount('#app')
}

void bootstrap()
