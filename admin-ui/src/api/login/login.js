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
// 登录
export const LoginCode= async (data)=>{
    try {
        const res = await instance.post("/auth/sso/login",data);
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