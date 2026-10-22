import instance from "@/api/interceptor";

// 发送验证码
export const SendCodes= async (email)=>{
    try {
        const res = await instance.get("/auth/sso/SendCode/"+email);
        return res.data
    } catch (e) {
        console.log(e);
    }
}
// 登录（带 source=admin，让后端走管理后台校验：禁止自动注册、必须有 admin 角色）
export const LoginCode= async (data)=>{
    try {
        const res = await instance.post("/auth/sso/login", { ...data, source: 'admin' });
        return res.data
    } catch (e) {
        console.log("111")
        console.log(e);
    }
}
// 注销
export const Logout= async ()=>{
    try {
        const res = await instance.get("/auth/sso/logout");
        return res.data
    } catch (e) {
        console.log(e);
    }
}