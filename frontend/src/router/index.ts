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
    }
  ]
})

// 登录闭环守卫：未携带 AT 一律回登录页（40101 拦截器兜底）
router.beforeEach((to) => {
  const auth = useAuthStore()
  if (!to.meta.public && !auth.accessToken) return { path: '/login' }
  if (to.path === '/login' && auth.accessToken) return { path: '/' }
  return true
})

export default router
