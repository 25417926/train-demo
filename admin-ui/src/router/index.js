import {createRouter, createWebHistory} from 'vue-router'
// import store from "@/store";
// import {message} from "ant-design-vue";

const routes = [
    {
        path: '/view',
        name: 'view',
        component: () => import('@/views/overView/index.vue'),
    },
    {
        path: '/',
        component: () => import('@/views/HomeView/index.vue'),
        meta: {
            requireAuth: true
        },
        children: [
            {
                path: 'welcome',
                component: () => import('@/views/ManageView/home/welcome.vue'),
                meta: {
                    requireAuth: true
                },
            },
            {
                path: 'about',
                component: () => import('@/views/ManageView/home/about.vue'),
            },
            {
                path: 'base/',
                children: [
                    {
                        path: 'station',
                        component: () => import('@/views/ManageView/base/station.vue'),
                    },
                    {
                        path: 'train',
                        component: () => import('@/views/ManageView/base/train.vue'),
                    },
                    {
                        path: 'train-station',
                        component: () => import('@/views/ManageView/base/train-station.vue'),
                    },
                    {
                        path: 'train-carriage',
                        component: () => import('@/views/ManageView/base/train-carriage.vue'),
                    },
                    {
                        path: 'train-seat',
                        component: () => import('@/views/ManageView/base/train-seat.vue'),
                    },
                ]
            },
            {
                path: 'batch/',
                children: [
                    {
                        path: 'job',
                        component: () => import('@/views/ManageView/batch/job.vue'),
                    },
                ]
            },
            {
                path: 'business/',
                children: [

                    {
                        path: 'daily-train',
                        component: () => import('@/views/businessView/daily-train.vue'),
                    },
                    {
                        path: 'daily-train-station',
                        component: () => import('@/views/businessView/daily-train-station.vue'),
                    },
                    {
                        path: 'daily-train-carriage',
                        component: () => import('@/views/businessView/daily-train-carriage.vue'),
                    },
                    {
                        path: 'daily-train-seat',
                        component: () => import('@/views/businessView/daily-train-seat.vue'),
                    },
                    {
                        path: 'daily-train-ticket',
                        component: () => import('@/views/businessView/daily-train-ticket.vue'),
                    },
                    {
                        path: 'confirm-order',
                        component: () => import('@/views/businessView/confirm-order.vue'),
                    },
                    {
                        path: 'business/',
                        children: [

                            {
                                path: 'daily-train',
                                component: () => import('@/views/businessView/daily-train.vue'),
                            },
                            {
                                path: 'daily-train-station',
                                component: () => import('@/views/businessView/daily-train-station.vue'),
                            },
                            {
                                path: 'daily-train-carriage',
                                component: () => import('@/views/businessView/daily-train-carriage.vue'),
                            },
                            {
                                path: 'daily-train-seat',
                                component: () => import('@/views/businessView/daily-train-seat.vue'),
                            },
                            {
                                path: 'daily-train-ticket',
                                component: () => import('@/views/businessView/daily-train-ticket.vue'),
                            },
                            {
                                path: 'confirm-order',
                                component: () => import('@/views/businessView/confirm-order.vue'),
                            },

                        ]
                    },

                    {
                        path: '',
                        redirect: '/welcome'
                    }

                    // {
                    //   path: '/about',
                    //   name: 'about',
                    //   // route level code-splitting
                    //   // this generates a separate chunk (about.[hash].js) for this route
                    //   // which is lazy-loaded when the route is visited.
                    //   component: () => import(/* webpackChunkName: "about" */ '../views/AboutView.vue')
                    // }
                ],

            },
            {
                path: 'member/',
                children: [

                    {
                        path: 'ticket',
                        component: () => import('@/views/MemberView/ticket.vue'),
                    },

                ]
            }
        ],
    }
]
const router = createRouter({
    history: createWebHistory(process.env.BASE_URL),
    routes
})
//
// //路由守卫
// router.beforeEach((to,from,next)=> {
//   // 判断该路由是否需要登录权限
//   if (to.matched.some(function (item) {
//     console.log(item, "是否需要登录校验：", item.meta.requireAuth || false);
//     return item.meta.requireAuth;
//   })) {
//     // 获取token
//     const _member = store.state.MemberToken;
//     console.log("页面登录校验开始：", _member);
//     // 判断是否已经登录
//     if (!_member.token) {
//       message.error("登录状态异常")
//       console.log("页面登录校验失败：", _member);
//       next('/view')
//     } else {
//       next()
//     }
//   }else {
//     next()
//   }
// })
export default router
