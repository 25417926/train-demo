<script >


import Login from "@/components/login/login.vue";
import Register from "@/components/login/register.vue";
import {ref} from "vue";

export default {
  name: 'overView',
  components: {Register, Login},
  data() {
      const current = ref(['login']);
    return {
      current,
      fn: true
    }
  },
  methods: {
    flag() {
      this.fn = !this.fn;
    },
  }
}
</script>

<template>
  <div class="container">
    <nav>
    <a-menu v-model:selectedKeys="current" mode="horizontal">
      <a-menu-item class="item" key="login" @click="flag">
        <template >
          <mail-outlined />
        </template>
        登录
      </a-menu-item>
      <a-menu-item class="item" key="reg" @click="flag">
        注册
      </a-menu-item>
    </a-menu>
    </nav>
    <login v-if="fn"></login>
    <register v-else></register>

  </div>

</template>

<style >
//* 整体页面的样式初始化 */
* {
  padding: 0;
  margin: 0;
  box-sizing: border-box;
}

body {
  /* 让页面整体在浏览器视口垂直和水平居中 */
  min-height: 100vh;
  position: relative;
}

/* 容器的样式 */
.container {
  width: 400px;
  max-width: 600px; /* 可以根据实际需求调整最大宽度 */
  text-align: center; /* 内部文本内容默认居中对齐 */
  border: 1px solid #ccc;
  margin: 0 auto; /* 水平居中容器 */
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%); /* 通过绝对定位和位移来实现垂直和水平居中 */
}

/* 导航栏的样式 */
nav {
  text-align: center; /* 让导航栏内的菜单项文本等内容水平居中 */
  margin-bottom: 15px;
  border: 1px solid #ccc;
}

/* 菜单相关的样式调整 */
.a-menu {
  width: 100%;
  max-width: 400px; /* 设置菜单最大宽度，防止内容过长撑开 */
  margin: 0 auto; /* 水平居中菜单 */
}

.a-menu-item {
  display: inline-block; /* 让菜单项可以水平排列并可以设置外边距等 */
  margin: 0 10px; /* 可以根据实际需求调整菜单项之间的间距 */
}

/* 登录和注册组件的样式（这里只是预留，你可根据实际组件内部样式完善） */
login,
register {
  width: 100%;
  max-width: 400px; /* 可以根据实际情况设置最大宽度 */
  margin: 0 auto; /* 让组件在父容器内水平居中 */
}
</style>