import axios from 'axios';
import { message } from 'ant-design-vue';
import store from "@/store";
import router from "@/router";
// 1. 创建新的axios实例
const instance = axios.create({
    // 设置接口域名
    baseURL: 'http://localhost:80',
    // 设置请求超时时间（注意这里timeout的值应该是数字类型，原代码中是字符串，修改为数字）
    timeout: 60000,
    // withCredentials: true
});

// 2. 请求拦截器（请求拦截器作用就是用于发送接口请求前需要做的操作）
instance.interceptors.request.use((config) => {
    // 发请求前做的一些处理，数据转化，配置请求头，设置token，设置loading等，根据需求去添加
    const token =store.state.Mobile.token
    if (token) { // 判断是否存在token，如果存在的话，则每个http header都加上token
        config.headers.satoken = token; // 请求头加上token
    }
    config.headers['Content-Type'] = 'application/json;charset=UTF-8';
    return config;
}, (error) => {
    return Promise.reject(error);
});

// 3. 响应拦截器（响应拦截器作用就是接收到响应数据后需要做的一些操作，比如判断接口状态）
instance.interceptors.response.use((res) => {
    // 接收到响应数据并成功后的一些共有的处理，关闭loading等
    // res为回调函数，也就是接口请求成功后返回的数据

    return res;
}, (error) => {
    // 处理响应出错的情况，比如网络错误等，可以在这里统一提示用户或者进行其他错误处理逻辑

    const res = error.response;
    //处理登录权限问题
    if(res && res.status === 401) {
        message.error("登录状态异常")
        store.commit("Mobile/clean")
        setTimeout(() => {
            router.push("/view")
        }, 1000)
    }else {
        // 这里可以统一提示错误信息
        message.error('接口请求出现错误，请稍后重试');
        return Promise.reject(error);
    }
});

export default instance;