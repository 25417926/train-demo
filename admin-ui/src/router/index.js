import { createRouter, createWebHistory } from 'vue-router';
import store from '@/store';
import { message } from 'ant-design-vue';

const routes = [
    // 登录页（不需要登录）
    {
        path: '/login',
        name: 'login',
        component: () => import('@/views/overView/index.vue'),
    },
    // 兼容旧路径 /view
    { path: '/view', redirect: '/login' },

    // 登录后主框架
    {
        path: '/',
        component: () => import('@/views/ManageView/index.vue'),
        meta: { requireAuth: true },
        redirect: '/welcome',
        children: [
            { path: 'welcome', component: () => import('@/views/ManageView/home/welcome.vue') },
            { path: 'about', component: () => import('@/views/ManageView/home/about.vue') },

            {
                path: 'base',
                children: [
                    { path: 'station', component: () => import('@/views/ManageView/base/station.vue') },
                    { path: 'train', component: () => import('@/views/ManageView/base/train.vue') },
                    { path: 'train-station', component: () => import('@/views/ManageView/base/train-station.vue') },
                    { path: 'train-carriage', component: () => import('@/views/ManageView/base/train-carriage.vue') },
                    { path: 'train-seat', component: () => import('@/views/ManageView/base/train-seat.vue') },
                ],
            },

            {
                path: 'batch',
                children: [
                    { path: 'job', component: () => import('@/views/ManageView/batch/job.vue') },
                ],
            },

            {
                path: 'business',
                children: [
                    { path: 'daily-train', component: () => import('@/views/businessView/daily-train.vue') },
                    { path: 'daily-train-station', component: () => import('@/views/businessView/daily-train-station.vue') },
                    { path: 'daily-train-carriage', component: () => import('@/views/businessView/daily-train-carriage.vue') },
                    { path: 'daily-train-seat', component: () => import('@/views/businessView/daily-train-seat.vue') },
                    { path: 'daily-train-ticket', component: () => import('@/views/businessView/daily-train-ticket.vue') },
                    { path: 'confirm-order', component: () => import('@/views/businessView/confirm-order.vue') },
                    { path: 'sk-token', component: () => import('@/views/businessView/sk-token.vue') },
                    { path: 'payment', component: () => import('@/views/businessView/payment.vue') },
                    { path: 'refund', component: () => import('@/views/businessView/refund.vue') },
                    { path: 'change-record', component: () => import('@/views/businessView/change-record.vue') },
                ],
            },

            {
                path: 'member',
                children: [
                    { path: 'ticket', component: () => import('@/views/MemberView/ticket.vue') },
                ],
            },
        ],
    },

    // 未匹配路径回首页
    { path: '/:pathMatch(.*)*', redirect: '/' },
];

const router = createRouter({
    history: createWebHistory(process.env.BASE_URL),
    routes,
});

// 路由守卫：requireAuth 路由必须有 token，否则回登录页
router.beforeEach((to, from, next) => {
    const needAuth = to.matched.some((r) => r.meta && r.meta.requireAuth);
    if (!needAuth) {
        return next();
    }
    const token = store.state.Mobile && store.state.Mobile.token;
    if (token) {
        return next();
    }
    message.error('请先登录');
    next('/login');
});

export default router;
