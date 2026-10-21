<template>
  <a-form :model="formState" name="normal_login" class="login-form">
    <a-form-item>
      <h2 style="text-align: center; margin-bottom: 2rem;">管理员登录</h2>
    </a-form-item>

    <a-form-item name="email" :rules="[{ required: true, message: 'Please input your email!' }]">
      <span style="margin-right: 8px">邮箱</span>
      <a-input v-model:value="formState.email" placeholder="请输入邮箱">
        <template #prefix>
          <UserOutlined class="site-form-item-icon"/>
        </template>
      </a-input>
    </a-form-item>

    <a-form-item name="password" :rules="[{ required: true, message: 'Please input your password!' }]">
      <span style="margin-right: 8px">密码:</span>
      <a-input-password v-model:value="formState.password" placeholder="请输入密码">
        <template #prefix>
          <LockOutlined class="site-form-item-icon"/>
        </template>
      </a-input-password>
    </a-form-item>

    <a-form-item>
      <div style="display: flex; justify-content: flex-end;">
        <a-checkbox v-model:checked="formState.remember">记住我</a-checkbox>
      </div>
    </a-form-item>

    <a-form-item>
      <a-button type="primary" html-type="submit" @click="login(formState.email, formState.password)"
                style="width: 25%; margin: 0 auto;">Log in</a-button>
    </a-form-item>
  </a-form>
</template>

<script>
import { reactive } from "vue";
import { LoginCode } from "@/api/login/login";
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
    });
    const router = useRouter();

    const login = async (email, password) => {
      if (!email) {
        message.error('请输入邮箱');
        return;
      }
      if (!password) {
        message.error('请输入密码');
        return;
      }
      try {
        const data = { email, password };
        const res = await LoginCode(data);
        if (res && res.code === 200) {
          message.success(res.message || '登录成功');
          store.commit('Mobile/set_token', res.content.saToken);
          store.commit('Mobile/set_mobile', res.content.id);
          await router.push('/welcome');
        } else if (res) {
          message.error(res.message || '登录失败');
        }
      } catch (e) {
        console.log(e);
      }
    };

    return {
      login,
      formState,
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
