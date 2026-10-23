<template>
  <a-layout style="min-height: 100vh">
    <a-layout-header class="header">
      <div class="logo">铁路售票管理后台</div>
      <div class="header-right">
        <span style="color: #fff; margin-right: 12px">您好，{{ memberId }}</span>
        <a-button size="small" @click="logout">退出登录</a-button>
      </div>
    </a-layout-header>
    <a-layout>
      <a-layout-sider width="220" theme="light">
        <a-menu
          mode="inline"
          :selected-keys="selectedKeys"
          :open-keys="openKeys"
          @update:openKeys="(v) => openKeys = v"
          style="height: 100%; border-right: 0"
        >
          <a-menu-item key="/welcome">
            <router-link to="/welcome">欢迎</router-link>
          </a-menu-item>

          <a-sub-menu key="base">
            <template #title>基础数据</template>
            <a-menu-item key="/base/station"><router-link to="/base/station">车站</router-link></a-menu-item>
            <a-menu-item key="/base/train"><router-link to="/base/train">车次</router-link></a-menu-item>
            <a-menu-item key="/base/train-station"><router-link to="/base/train-station">车次车站</router-link></a-menu-item>
            <a-menu-item key="/base/train-carriage"><router-link to="/base/train-carriage">车厢</router-link></a-menu-item>
            <a-menu-item key="/base/train-seat"><router-link to="/base/train-seat">座位</router-link></a-menu-item>
          </a-sub-menu>

          <a-sub-menu key="business">
            <template #title>业务数据</template>
            <a-menu-item key="/business/daily-train"><router-link to="/business/daily-train">每日车次</router-link></a-menu-item>
            <a-menu-item key="/business/daily-train-station"><router-link to="/business/daily-train-station">每日车站</router-link></a-menu-item>
            <a-menu-item key="/business/daily-train-carriage"><router-link to="/business/daily-train-carriage">每日车厢</router-link></a-menu-item>
            <a-menu-item key="/business/daily-train-seat"><router-link to="/business/daily-train-seat">每日座位</router-link></a-menu-item>
            <a-menu-item key="/business/daily-train-ticket"><router-link to="/business/daily-train-ticket">每日余票</router-link></a-menu-item>
            <a-menu-item key="/business/confirm-order"><router-link to="/business/confirm-order">确认订单</router-link></a-menu-item>
            <a-menu-item key="/business/sk-token"><router-link to="/business/sk-token">秒杀令牌</router-link></a-menu-item>
            <a-menu-item key="/business/payment"><router-link to="/business/payment">支付记录</router-link></a-menu-item>
            <a-menu-item key="/business/refund"><router-link to="/business/refund">退款记录</router-link></a-menu-item>
            <a-menu-item key="/business/change-record"><router-link to="/business/change-record">退改签记录</router-link></a-menu-item>
            <a-menu-item key="/business/waitlist"><router-link to="/business/waitlist">候补列表</router-link></a-menu-item>
          </a-sub-menu>

          <a-sub-menu key="batch">
            <template #title>定时任务</template>
            <a-menu-item key="/batch/job"><router-link to="/batch/job">任务列表</router-link></a-menu-item>
          </a-sub-menu>

          <a-sub-menu key="member">
            <template #title>会员</template>
            <a-menu-item key="/member/ticket"><router-link to="/member/ticket">车票</router-link></a-menu-item>
          </a-sub-menu>
        </a-menu>
      </a-layout-sider>
      <a-layout-content style="background: #fff; padding: 24px; margin: 0; min-height: 280px">
        <router-view/>
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script>
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useStore } from 'vuex';
import { Logout } from '@/api/login/login';
import { message } from 'ant-design-vue';

export default {
  name: 'ManageLayout',
  setup() {
    const store = useStore();
    const router = useRouter();
    const route = useRoute();

    const memberId = computed(() => store.state.Mobile.mobile);

    const selectedKeys = ref([route.path]);
    const openKeys = ref(['base', 'business', 'batch', 'member']);

    watch(() => route.path, (p) => {
      selectedKeys.value = [p];
    });

    const logout = async () => {
      try {
        await Logout();
      } catch (e) {
        console.log(e);
      }
      store.commit('Mobile/clean');
      message.success('已退出');
      router.push('/login');
    };

    return { memberId, selectedKeys, openKeys, logout };
  },
};
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #001529;
  padding: 0 24px;
}
.logo {
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 1px;
}
.header-right {
  display: flex;
  align-items: center;
}
</style>
