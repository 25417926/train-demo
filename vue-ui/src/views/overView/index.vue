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
*{
  /*初始化 清除页面元素的内外边距*/
  padding: 0;
  margin: 0;
  /*盒子模型*/
  box-sizing: border-box;
}
body {
  /*弹性布局 让页面元素垂直+水平居中*/
  display: flex;
  justify-content: center;
  align-items: center;
  /*让页面始终占浏览器可视区域总高度*/
  height: 100vh;
  /*背景渐变色*/
  background: linear-gradient(#141e30,#243b55);
}
nav{
  position: relative;
  left: -25px;
}
.ant-menu{
  background-color: transparent;
}
nav .item{
  border: none;
  width: 50px;
  padding-right: 10px;
  color: #fff;
  font-size: 20px;
  /*背景颜色为透明色*/
  background-color: transparent;
  transition: color 1s;
}
.ant-menu-horizontal{
 border: none;
}
.ant-menu-horizontal:not(.ant-menu-dark) > .ant-menu-item:hover::after, .ant-menu-horizontal:not(.ant-menu-dark) > .ant-menu-submenu:hover::after, .ant-menu-horizontal:not(.ant-menu-dark) > .ant-menu-item-active::after, .ant-menu-horizontal:not(.ant-menu-dark) > .ant-menu-submenu-active::after, .ant-menu-horizontal:not(.ant-menu-dark) > .ant-menu-item-open::after, .ant-menu-horizontal:not(.ant-menu-dark) > .ant-menu-submenu-open::after, .ant-menu-horizontal:not(.ant-menu-dark) > .ant-menu-item-selected::after, .ant-menu-horizontal:not(.ant-menu-dark) > .ant-menu-submenu-selected::after
{
  border-bottom: none;
}
nav button:focus{
  border-bottom: #03e9f4 2px solid;
}
.container{
  border: #141e30 solid 1px;
    /*弹性布局 让子元素称为弹性项目*/
    display: flex;
    /*让弹性项目垂直排列  原理是改变弹性盒子的主轴方向
    父元素就是弹性盒子  现在改变后的主轴方向是向下了*/
    flex-direction: column;
    /*让弹性项目在交叉轴方向水平居中  现在主轴的方向是向下
    交叉轴的方向是与主轴垂直 交叉轴的方向是向右*/
    align-items: center;
    width: 400px;
    padding: 40px;
    background-color: rgba(0, 0, 0, 0.2);
    box-shadow: 0 15px 25px rgba(0, 0, 0, 0.4);

}
</style>