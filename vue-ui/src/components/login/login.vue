<template>
  <a-form :model="formState" name="normal_login" class="login-form">
    <a-form-item>
      <h2 style="text-align: center; margin-bottom: 2rem;">用户登录</h2>
    </a-form-item>

    <a-form-item name="email" :rules="[{ required: true, message: 'Please input your email!' }]">
      <span style="margin-right: 8px">邮箱</span>
      <a-input v-model:value="formState.email" placeholder="请输入邮箱">
        <template #prefix>
          <UserOutlined class="site-form-item-icon"/>
        </template>
      </a-input>
    </a-form-item>

    <a-form-item name="code" v-if="flag" :rules="[{ required: true, message: 'Please input the code!' }]">
      <span style="margin-right: 8px">验证码:</span>
      <div style="display: flex; align-items: center;">
        <a-input v-model:value="formState.code" placeholder="请输入验证码" style="flex: 1; margin-right: 8px;"></a-input>
        <a-button type="primary" @click="SendsCodes" :disabled="countdown > 0">{{ countdown ? `${countdown}秒后重发` : '获取验证码' }}</a-button>
      </div>
    </a-form-item>

    <a-form-item name="password" v-else :rules="[{ required: true, message: 'Please input your password!' }]">
      <span style="margin-right: 8px">密码:</span>
      <a-input-password v-model:value="formState.password" placeholder="请输入密码">
        <template #prefix>
          <LockOutlined class="site-form-item-icon"/>
        </template>
      </a-input-password>
    </a-form-item>

    <a-form-item>
      <div style="display: flex; justify-content: space-between;">
        <a-checkbox v-model:checked="flag">验证码登录</a-checkbox>
        <a-checkbox v-model:checked="formState.remember">记住我</a-checkbox>
      </div>
    </a-form-item>

    <a-form-item>
      <a-button type="primary" html-type="submit" @click="login(formState.email, formState.password, formState.code)"
                style="width: 25%; margin: 0 auto;">Log in</a-button>
    </a-form-item>
  </a-form>
</template>

<script>
import { reactive, ref } from "vue";
import { LoginCode } from "@/api/login";
import { getSendCode } from "@/api/Order";
import { useRouter } from 'vue-router';
import { UserOutlined, LockOutlined } from '@ant-design/icons-vue';
import { message } from "ant-design-vue";
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
      email: '',
      password: '',
      remember: true,
      code: '',
      codes: null,
      flesh: false,
    });
    const flag = ref(true);
    const SendCode = ref();
    const router = useRouter();

    let countdown = ref(0);

    const startCountdown = () => {
      countdown.value = 60;
      const timer = setInterval(() => {
        if (countdown.value > 0) {
          countdown.value--;
        } else {
          clearInterval(timer);
        }
      }, 1000);
    };

    const validateEmail = (email) => {
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      return emailRegex.test(email);
    };

    const SendsCodes = async () => {
      try {
        // 验证邮箱是否为空
        if (!formState.email) {
          message.error('请输入邮箱');
          return;
        }

        // 验证邮箱格式是否正确
        if (!validateEmail(formState.email)) {
          message.error('请输入正确的邮箱格式');
          return;
        }

        const res = await getSendCode(formState.email);
        if (res.code === 200) {
          // SendCode.value = "data:image/gif;base64," + res.content || null;
          startCountdown();
          message.success('验证码发送成功');
        }
      } catch (e) {
        console.log(e);
      }
    };

    const login = async (email, password, code) => {
      try {
        const data = { email: email, password: password, code: code };
        const res = await LoginCode(data);
        if (res.code === 200) {
          console.log(res)
          message.success(res.message);
          router.push('/');
          store.commit('Mobile/set_token', res.content.saToken);
          store.commit('Mobile/set_mobile', res.content.id);
        }
      } catch (e) {
        console.log(e);
      }
    };

    return {
      SendsCodes,
      login,
      SendCode,
      formState,
      countdown,
      flag
    };
  },
};
</script>

<style scoped>
/* 登录表单整体样式 */
.login-form {
  width: 100%;
  max-width: 320px;
  margin: 0 auto;
  padding: 2rem;
  background: white;
  border-radius: 12px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

/* 表单项内文本等内容水平居中 */
.a-form-item {
  margin-bottom: 16px;
  text-align: center;
}

/* 输入框样式 */
.a-input,
.a-input-password {
  width: 100%;
}

/* 按钮样式 */
.a-button {
  width: 25%;
  margin: 0 auto;
}

/* 图标样式 */
.site-form-item-icon {
  margin-right: 8px;
}

/* 复选框样式 */
.a-checkbox {
  margin-top: 8px;
}

/* 响应式处理 */
@media (max-width: 480px) {
  .login-form {
    padding: 1rem;
  }

  .a-input {
    width: 100% !important;
  }

  img[src^="data:image"] {
    max-width: 80px;
  }
}
</style>
