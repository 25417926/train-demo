<template>
  <a-layout >
    <a-layout-header class="header" id="components-layout-demo-top-side-2">
      <div class="logo" >
        <div style="margin-top:-17px">
        <router-link to="/welcome">12306</router-link>
        </div>
      </div>
      <div style="float: right;color: white">您好：{{mobile}}
        <button @click="layout()" class="layout">退出登录</button>
      </div>
      <a-menu
          v-model:selectedKeys="SelectKey"
          theme="dark"
          mode="horizontal"
          :style="{ lineHeight: '64px' }"
      >
        <a-menu-item key="welcome"><router-link to="/welcome">欢迎</router-link></a-menu-item>
        <a-menu-item key="about" ><router-link to="/about">关于</router-link></a-menu-item>
        <a-menu-item key="station" ><router-link to="/station">车站信息</router-link></a-menu-item>
        <a-menu-item key="train" ><router-link to="/train">火车信息</router-link></a-menu-item>
      </a-menu>

    </a-layout-header>
    <a-layout>
      <a-layout-sider width="200" style="background: #fff">
        <a-menu
            v-model:selectedKeys="SelectKey"
            v-model:openKeys="openKeys"
            mode="inline"
            :style="{ height: '100%', borderRight: 0 }"
        >
          <a-sub-menu key="sub1">
            <template #title>
              <span>
                <user-outlined />
                管理信息
              </span>
            </template>
            <a-menu-item key="welcome"><router-link to="/welcome">欢迎</router-link></a-menu-item>
            <a-menu-item key="about" ><router-link to="/about">关于</router-link></a-menu-item>
            <a-menu-item key="station" ><router-link to="/station">车站信息</router-link></a-menu-item>
            <a-menu-item key="train" ><router-link to="/train">火车信息</router-link></a-menu-item>
<!--            <a-menu-item key="3">option3</a-menu-item>-->
<!--            <a-menu-item key="4">option4</a-menu-item>-->
          </a-sub-menu>
<!--          <a-sub-menu key="sub2">-->
<!--            <template #title>-->
<!--              <span>-->
<!--                <laptop-outlined />-->
<!--                subnav 2-->
<!--              </span>-->
<!--            </template>-->
<!--            <a-menu-item key="5">option5</a-menu-item>-->
<!--            <a-menu-item key="6">option6</a-menu-item>-->
<!--            <a-menu-item key="7">option7</a-menu-item>-->
<!--            <a-menu-item key="8">option8</a-menu-item>-->
<!--          </a-sub-menu>-->
<!--          <a-sub-menu key="sub3">-->
<!--            <template #title>-->
<!--              <span>-->
<!--                <notification-outlined />-->
<!--                subnav 3-->
<!--              </span>-->
<!--            </template>-->
<!--            <a-menu-item key="9">option9</a-menu-item>-->
<!--            <a-menu-item key="10">option10</a-menu-item>-->
<!--            <a-menu-item key="11">option11</a-menu-item>-->
<!--            <a-menu-item key="12">option12</a-menu-item>-->
<!--          </a-sub-menu>-->
        </a-menu>
      </a-layout-sider>
      <a-layout >
        <a-layout-content
            :style="{ background: '#fff',padding: '24px',  margin: 0, minHeight: '280px' }"
        >
          <router-view/>
        </a-layout-content>
      </a-layout>
    </a-layout>
  </a-layout>
</template>
<script>
import { UserOutlined} from '@ant-design/icons-vue';
import { defineComponent, ref ,watch} from 'vue';
import {useStore} from "vuex";
import { useRouter } from 'vue-router';

export default defineComponent({
  name: 'HomeView',
  components: {
    UserOutlined,
    // LaptopOutlined,
    // NotificationOutlined,
  },
  setup() {
    // 获取vuex
    const store = useStore()
    const router = useRouter()
    // 获取手机号
    const mobile =store.state.MemberToken.mobile
    //获取选中
    const SelectKey = ref([])
    //观察路由是否变化，然后传给SelectKey来保证数据一致然后实现同步
    watch(()=>router.currentRoute.value.path, (newValue)=> {
      console.log(newValue)
      SelectKey.value = []
      SelectKey.value.push(newValue);
    },{immediate:true})
    // 退出登录
    const layout = async ()=>{
      store.commit('set_token',null)
      store.commit('set_mobile',null)
      router.push('/view')
    }
    return {
      selectedKeys1: ref(['2']),
      selectedKeys2: ref(['1']),
      collapsed: ref(false),
      openKeys: ref(['sub1']),
      layout,
      mobile,
      SelectKey
    };
  },

});
</script>
<style >
#components-layout-demo-top-side-2 .logo {
  float: left;
  width: 120px;
  height: 30px;
  text-align: center;

  margin: 16px 24px 16px 0;
  background: rgba(255, 255, 255, 0.3);
}

.layout{
  background-color: transparent;
  border: none;
  transition:0.5s ease-in-out;
}
.layout:hover{
  color: #3ff;
}
</style>