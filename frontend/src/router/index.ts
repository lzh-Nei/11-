import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import LoginView from '@/views/LoginView.vue'
import RegisterView from '@/views/RegisterView.vue'
import ForgotPasswordView from '@/views/ForgotPasswordView.vue'
import HomeView from '@/views/HomeView.vue'
import StudentTasks from '@/views/StudentTasks.vue'
import StudentSubmit from '@/views/StudentSubmit.vue'
import StudentSubmissions from '@/views/StudentSubmissions.vue'
import EvaluationReport from '@/views/EvaluationReport.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
    // W8-C3 UC-43/44：注册与找回密码（公开路由，与/login同级）
    { path: '/register', name: 'register', component: RegisterView, meta: { public: true } },
    {
      path: '/forgot-password',
      name: 'forgot-password',
      component: ForgotPasswordView,
      meta: { public: true }
    },
    // WP8-B0 设计系统样板页（风格裁决门，公开路由；全站唯一允许 mock 处）
    {
      path: '/style-guide',
      name: 'style-guide',
      component: () => import('@/views/StyleGuideView.vue'),
      meta: { public: true }
    },
    { path: '/', name: 'home', component: HomeView },
    // M1 学生链路页（W7-C1）：任务列表 → 提交作业 → 提交记录
    { path: '/tasks', name: 'student-tasks', component: StudentTasks },
    { path: '/tasks/:id/submit', name: 'student-submit', component: StudentSubmit },
    { path: '/submissions', name: 'student-submissions', component: StudentSubmissions },
    // M1 演示终点画面（W7-C2 UC-21）：AI评价报告
    { path: '/evaluations/:id', name: 'evaluation-report', component: EvaluationReport },
    // WP8-B3 教师工作台落位（Evaluate/Check 占位启用；页面内自校 TEACHER 权限）
    { path: '/tasks/manage', name: 'teacher-tasks', component: () => import('@/views/Check.vue') },
    {
      path: '/evaluations/:id/review',
      name: 'teacher-review',
      component: () => import('@/views/Evaluate.vue')
    },
    // WP8-B6 文件中心：分片上传产物清单 + 流式直下（/api/files + /api/download/{id}）
    {
      path: '/files',
      name: 'file-center',
      component: () => import('@/views/FileCenterView.vue')
    },
    // WP8-B8 登录后自助改密（UC-07：A-06 邮箱码 → A-10 重置，成功清态回登录）
    {
      path: '/change-password',
      name: 'change-password',
      component: () => import('@/views/ChangePasswordView.vue')
    },
    // WP8-B6 全局错误页（#02）：403/500 直达 + history 兜底 404
    {
      path: '/403',
      name: 'forbidden',
      component: () => import('@/views/ErrorView.vue'),
      meta: {
        public: true,
        code: 403,
        errorTitle: '无权限访问',
        errorDesc: '当前账号没有访问该页面的权限。若认为这是误判，请联系授课教师核对角色与权限点。'
      }
    },
    {
      path: '/500',
      name: 'server-error',
      component: () => import('@/views/ErrorView.vue'),
      meta: {
        public: true,
        code: 500,
        errorTitle: '服务暂时不可用',
        errorDesc: '服务端处理请求时出现异常，请稍后重试；若持续出现，请把时间和操作路径反馈给管理员。'
      }
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/ErrorView.vue'),
      meta: {
        public: true,
        code: 404,
        errorTitle: '页面不存在',
        errorDesc: '链接可能已失效，或地址输入有误。请检查地址，或从首页重新进入。'
      }
    }
  ]
})

// 登录闭环守卫：未携带 AT 一律回登录页（40101 拦截器兜底）；
// 硬刷新后 token 在而 /me 不在 → 先装载用户态，避免角色/权限视角整体降级
router.beforeEach(async (to) => {
  const auth = useAuthStore()
  const needAuth = !to.meta.public
  if (needAuth && !auth.accessToken) return { path: '/login' }
  if (auth.accessToken && !auth.me) {
    try {
      await auth.loadMe()
    } catch {
      auth.clear()
      if (needAuth) return { path: '/login' }
    }
  }
  if (to.path === '/login' && auth.accessToken) return { path: '/' }
  return true
})

export default router
