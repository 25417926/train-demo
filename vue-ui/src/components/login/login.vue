<script>

import {reactive, ref} from "vue";
import {LoginCode} from "@/api/login"
import {getSendCode} from "@/api/Order"
import { useRouter } from 'vue-router';

import { UserOutlined, LockOutlined } from '@ant-design/icons-vue';
import {message} from "ant-design-vue";
import { useStore } from 'vuex';

export default {
  name: "loginView",
  components: {
    UserOutlined,
    LockOutlined,
  },
  setup() {
    const store = useStore();
    const formState = reactive({
      username: '',
      password: '',
      remember: true,
      code:null,
      codes:null,
      flesh: false,
    });
    const SendCode = ref();
    const router = useRouter();
    const LoginToken = ref();
    let count = 0
    // 使用箭头函数，这里的 this 会正确指向 Vue 实例
    const SendsCodes = async () => {
      try {
        count = count + 1
        console.log(count)
        if(count > 5){
          message.error("验证码获取过快，请稍后再试")
          return
        }else {
          setTimeout(()=>{
            count = 0
          }, 10000)
        }
        LoginToken.value =window.Tool.uuid(8)
        const res = await getSendCode(LoginToken.value);

        // 通过.value 属性来更新 ref 类型的响应式数据的值
        if(res.code === 200) {
          SendCode.value = "data:image/gif;base64," + res.content || null;
        }
      } catch (e) {
        console.log(e);
      }
    };
    const login = async (mobile,password, code) => {
      try {
        const data = {mobile: mobile, password: password, code: code, TokenCode: LoginToken.value}

        const res = await LoginCode(data);
        if (res.code === 200) {
          message.success(res.message)
          router.push('/')
          store.commit('Mobile/set_token', res.content.token)
          store.commit('Mobile/set_mobile', res.content.mobile)
        }
      }catch (e){
        console.log(e);
      }
    };
    return {
      SendsCodes,
      login,
      SendCode,
      formState,
      count,
      LoginToken
    };
  },
  mounted() {
    this.SendsCodes();
  },
};
</script>

<template>
  <a-form
      :model="formState"
      name="normal_login"
      class="login-form"
      @finish="onFinish"
      @finishFailed="onFinishFailed"
  >
    <a-form-item

        name="username"
        :rules="[{ required: true, message: 'Please input your username!' }]"
    >
      <span style="margin-right: 8px">用户名</span>
      <a-input v-model:value="formState.username" style="width: 260px">
        <template #prefix>
          <UserOutlined class="site-form-item-icon" />
        </template>
      </a-input>
    </a-form-item>

    <a-form-item
        name="password"
        :rules="[{ required: true, message: 'Please input your password!' }]"
    >
      <span style="margin-right: 8px">密&emsp;码:</span>
      <a-input-password v-model:value="formState.password" style="width: 260px">
        <template #prefix>
          <LockOutlined class="site-form-item-icon" />
        </template>
      </a-input-password>
    </a-form-item>
    <a-form-item  name="code" >
      <span style="margin-right: 8px">验证码:</span>
      <a-input v-model:value="formState.code" style="width: calc(65% )" >
        <template #suffix>
          <img :src="SendCode" @click="SendsCodes">
        </template>
      </a-input>
    </a-form-item>
    <a-form-item>
      <a-form-item name="remember" no-style>
        <a-checkbox v-model:checked="formState.remember">Remember me</a-checkbox>
      </a-form-item>
    </a-form-item>
    <a-form-item>
      <a-button @click="login(formState.username,formState.password,formState.code)" type="primary" html-type="submit" class="login-form-button">
        Log in
      </a-button>
    </a-form-item>
  </a-form>
</template>

<style >
/* 全局样式初始化，清除默认边距等 */
* {
  padding: 0;
  margin: 0;
  box-sizing: border-box;
}
.normal_login{
  margin-left: 100px;
}
.ant-form-item-control-input{
  justify-self: center;
}
/* 让body元素占满整个浏览器可视区域高度，并使用flex布局使其内部子元素能方便地居中对齐 */


/* 登录表单整体样式，设置宽度并使其居中显示 */


/* 表单内各表单项样式 */
.a-form-item {
  margin-bottom: 16px;
  text-align: center; /* 表单项内文本等内容水平居中 */
}

/* 输入框样式 */
.a-input,
.a-input-password {
  width: 100%;
}

/* 按钮样式 */
.a-button {
  width: 100%;
}

/* 图标样式 */
.site-form-item-icon {
  margin-right: 8px;
}

/* 复选框样式 */
.a-checkbox {
  margin-top: 8px;
}
</style>