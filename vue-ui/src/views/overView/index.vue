<template>
  <div class="container">

    <!-- 添加尺寸约束容器 -->
    <div class="login-wrapper">
      <login></login>
    </div>
  </div>
</template>

<script>
import Login from "@/components/login/login.vue";

export default {
  name: 'overView',
  components: {Login},
};
</script>

<style scoped>
/* 整体页面优化 */
body {
  min-height: 100vh;
  position: relative;
  background: linear-gradient(135deg, #f0f2f5 0%, #e6e9ed 100%);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

.login-wrapper {
  flex: 1;
  min-height: 300px;
  display: flex;
  align-items: center;
}

/* 修改容器定位逻辑 */
.container {
  width: 90%;
  max-width: 420px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  padding: 2rem;

  /* 使用视口单位定位 */
  position: fixed;
  top: 50vh;
  left: 50vw;
  transform: translate(-50%, -50%);

  /* 添加弹性布局 */
  display: flex;
  flex-direction: column;
  gap: 1.5rem;

  /* 滚动控制 */
  max-height: 90vh;
  overflow-y: auto;
}

/* 导航栏升级 */
nav {
  margin-bottom: 2rem;
  padding: 1rem 0;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

nav h2 {
  color: #2c3e50;
  font-size: 1.5rem;
  font-weight: 600;
  margin: 0;
  letter-spacing: 0.5px;
}

/* 响应式优化 */
@media (max-width: 480px) {
  .container {
    width: 95%;
    padding: 1.5rem;
  }

  nav h2 {
    font-size: 1.3rem;
  }
}

/* 动画增强 */
@keyframes fadeIn {
  from { opacity: 0; transform: translate(-50%, -45%); }
  to { opacity: 1; transform: translate(-50%, -50%); }
}
</style>
