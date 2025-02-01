<template>
  <div class="chat-container">
    <h1>AI 对话助手</h1>
    <div class="chat-box">
      <div
        v-for="(message, index) in messages"
        :key="index"
        class="message"
        :class="{
          'user-message': message.role === 'user',
          'ai-message': message.role === 'ai'
        }"
      >
        <div class="message-avatar">
          <div>
            {{ message.role === 'user' ? '用户' : '小布'}}
          </div>
        </div>
        <div class="message-content">
          <p>
            <span>{{ message.content }}</span>
          </p>
        </div>
      </div>
    </div>
    <div class="input-container">
      <input v-model="userMessage" placeholder="输入你的消息..."/>
      <button @click="sendMessage()">发送</button>
    </div>
  </div>
</template>

<script>
import { getChatList } from '@/api/AiChat';
import { ref } from "vue";

export default {
  name: "welcomeView",
  setup() {
    const messages = ref([]); // 使用 const 定义并确保返回的是 ref 对象
    const userMessage = ref(''); // 修改为与模板中一致的变量名

    const sendMessage = async () => {
      if (userMessage.value.trim() === '') return; // 防止发送空消息

      messages.value.push({ role: 'user', content: userMessage.value });
      console.log(messages.value); // 确保打印的是 messages.value

      try {
        const response = await getChatList(userMessage.value);
        if (response && response.content) {
          messages.value.push({ role: 'ai', content: response.content });
        } else {
          console.error('Unexpected response format:', response);
        }
      } catch (error) {
        console.error('Error fetching chat list:', error);
      }
    };

    return {
      messages, // 直接返回 ref 对象
      userMessage, // 修改为与模板中一致的变量名
      sendMessage
    };
  }
}
</script>


<style scoped>
.chat-container {
  width: 100%;
  margin: 0 ;
  border: 1px solid #ccc;

  overflow: hidden;
  box-shadow: 0 0 10px rgba(0, 0, 0, 0.1);
}

h1 {
  background-color: #007BFF;
  color: white;
  text-align: center;
  padding: 10px;
  margin: 0;
}

.chat-box {
  height: 300px;
  overflow-y: auto;
  padding: 10px;
  background-color: #f9f9f9;
}
p{
  margin: 0;
  padding: 0;
}
.message {
  display: flex;
  margin-bottom: 10px;
  align-items: flex-start;
}

.message-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  overflow: hidden;
  margin-right: 10px;
}

.message-avatar div {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.message-content {
  max-width: 70%;
  padding: 8px;
  border-radius: 8px;
}

.user-message {
  justify-content: flex-end;
}

.user-message .message-content {
  background-color: #DCF8C6;
  order: -1;
  margin-right: 10px;
}

.ai-message .message-content {
  background-color: #E5E5EA;
}

.input-container {
  display: flex;
  padding: 10px;
  background-color: white;
  border-top: 1px solid #ccc;
}

.input-container input {
  flex: 1;
  padding: 8px;
  border: 1px solid #ccc;
  border-radius: 4px;
  margin-right: 10px;
}

.input-container button {
  padding: 8px 16px;
  background-color: #007BFF;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.input-container button:hover {
  background-color: #0056b3;
}
</style>