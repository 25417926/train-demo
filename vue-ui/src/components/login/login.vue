<script>

import {reactive, ref} from "vue";
import {SendCodes,LoginCode} from "@/api/login"
import { useRouter } from 'vue-router';
import { UserOutlined, LockOutlined } from '@ant-design/icons-vue';

export default {
  name: "loginView",
  components: {
    UserOutlined,
    LockOutlined,
  },
  setup() {
    const formState = reactive({
      username: '',
      password: '',
      remember: true,
    });
    const SendCode = ref('1234');
    const router = useRouter();
    // 使用箭头函数，这里的 this 会正确指向 Vue 实例
    const SendsCodes = async () => {
      try {
        const res = await SendCodes();
        // 通过.value 属性来更新 ref 类型的响应式数据的值
        SendCode.value = res.content;
      } catch (e) {
        console.log(e);
      }
    };
    const login = async (mobile, code, pwd) => {
      try {
        const data = {mobile: mobile, code: code, pwd: pwd}
        const res = await LoginCode(data);
        if (res.code === 200) {
          router.push('/home');
        }
        console.log(res);
      }catch (e){
        console.log(e);
      }
    };
    return {
      SendsCodes,
      login,
      SendCode,
      formState
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
        label="Username"
        name="username"
        :rules="[{ required: true, message: 'Please input your username!' }]"
    >
      <a-input v-model:value="formState.username">
        <template #prefix>
          <UserOutlined class="site-form-item-icon" />
        </template>
      </a-input>
    </a-form-item>

    <a-form-item
        label="Password"
        name="password"
        :rules="[{ required: true, message: 'Please input your password!' }]"
    >
      <a-input-password v-model:value="formState.password">
        <template #prefix>
          <LockOutlined class="site-form-item-icon" />
        </template>
      </a-input-password>
    </a-form-item>

    <a-form-item>
      <a-form-item name="remember" no-style>
        <a-checkbox v-model:checked="formState.remember">Remember me</a-checkbox>
      </a-form-item>
      <a class="login-form-forgot" href="">Forgot password</a>
    </a-form-item>

    <a-form-item>
      <a-button :disabled="disabled" type="primary" html-type="submit" class="login-form-button">
        Log in
      </a-button>
      Or
      <a href="">register now!</a>
    </a-form-item>
  </a-form>
</template>

<style>
#components-form-demo-normal-login .login-form {
  max-width: 300px;
}
#components-form-demo-normal-login .login-form-forgot {
  float: right;
}
#components-form-demo-normal-login .login-form-button {
  width: 100%;
}
</style>