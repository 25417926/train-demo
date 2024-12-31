import { createRouter, createWebHistory } from 'vue-router'
// import store from "@/store";
// import {message} from "ant-design-vue";

const routes = [
  {
    path: '/view',
    name: 'view',
    component: ()=> import('@/views/overView/index.vue'),
  },
  {
    path: '/',
    component: ()=> import('@/views/HomeView/index.vue'),
    meta:{
      requireAuth:true
    },
    children:[
      {
        path: 'welcome',
        component: ()=> import('@/views/HomeView/welcome.vue'),
        meta:{
          requireAuth:true
        },
      },
      {
        path: 'about',
        component: () => import('@/views/HomeView/about.vue'),
      },
      {
        path: 'station',
        component: ()=> import('@/views/HomeView/station.vue'),
      },
      {
        path: 'train',
        component: ()=> import('@/views/HomeView/train.vue'),
      }
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
