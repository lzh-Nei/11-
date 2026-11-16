import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import LoginView from '@/views/LoginView.vue'
import RegisterView from '@/views/RegisterView.vue'
import ForgotPasswordView from '@/views/ForgotPasswordView.vue'
import AppLayout from '@/components/layout/AppLayout.vue'
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
    { path: '/forgot-password', name: 'forgot-password', component: ForgotPasswordView, meta: { public: true } },

    /* ══════════ 应用外壳（WP8-C1）：左侧模块导航 + 右侧模块页面
       下钻页（提交作业/评价报告/复核台）同样挂在外壳下，新窗口打开时保持左右布局 ══════════ */
    {
      path: '/',
      component: AppLayout,
      children: [
        { path: '', name: 'home', redirect: '/modules' },
        {
          path: 'tasks',
          name: 'student-tasks',
          component: StudentTasks,
          meta: { roles: ['STUDENT'] }
        },
        {
          path: 'tasks/manage',
          name: 'teacher-tasks',
          redirect: '/modules?group=teaching&child=task-list',
          meta: { roles: ['TEACHER', 'ADMIN'] }
        },
        {
          path: 'tasks/:id/submit',
          name: 'student-submit',
          component: StudentSubmit,
          meta: { roles: ['STUDENT'] }
        },
        {
          path: 'submissions',
          name: 'student-submissions',
          component: StudentSubmissions,
          meta: { roles: ['STUDENT'] }
        },
        {
          path: 'my-evaluations',
          name: 'my-evaluations',
          redirect: '/modules?group=study&child=submissions',
          meta: { roles: ['STUDENT'] }
        },
        {
          // 旧版报表驾驶舱书签兼容：功能已并入教学/学习分析模块
          path: 'report.html',
          name: 'legacy-report',
          redirect: '/modules?group=analysis&child=report'
        },
        { path: 'evaluations/:id', name: 'evaluation-report', component: EvaluationReport },
        {
          path: 'review',
          name: 'review-hub',
          component: () => import('@/views/ReviewHubView.vue'),
          meta: { roles: ['TEACHER', 'ADMIN'] }
        },
        {
          path: 'evaluations/:id/review',
          name: 'teacher-review',
          component: () => import('@/views/Evaluate.vue'),
          meta: { roles: ['TEACHER', 'ADMIN'] }
        },
        {
          path: 'files',
          name: 'file-center',
          component: () => import('@/views/FileCenterView.vue'),
          meta: { roles: ['ADMIN'] }
        },
        {
          path: 'modules',
          name: 'modules',
          component: () => import('@/views/ModulesView.vue')
        },
        {
          // 旧 /system 书签兼容跳转（管理端能力已并入功能模块）
          path: 'system',
          redirect: '/modules',
          meta: { roles: ['ADMIN'] }
        },
        { path: 'account', name: 'account', component: () => import('@/views/AccountView.vue') },
        {
          path: 'change-password',
          name: 'change-password',
          component: () => import('@/views/ChangePasswordView.vue')
        }
      ]
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
        errorDesc: '当前账号没有访问该页面的权限。若认为这是误判，请联系授课教师核对角色与权限。'
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
        errorDesc: '服务暂时不可用，请稍后重试；若持续出现，请把时间和操作步骤反馈给管理员。'
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
  if (to.path === '/login' && auth.accessToken) return { path: '/modules' }
  // 模块级角色门控（权限矩阵之外的角色硬边界，越权→403 页）
  const roles = to.meta.roles as string[] | undefined
  if (roles?.length && auth.me) {
    const mine = auth.me.roles ?? []
    if (!roles.some((r) => mine.includes(r))) return { path: '/403' }
  }
  return true
})

export default router
