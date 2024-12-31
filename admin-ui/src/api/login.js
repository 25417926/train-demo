import instance from "@/api/interceptor";

// 发送验证码
export const SendCodes= async ()=>{
    try {
        const res = await instance.get("/member/SendCode");
        return res.data
    } catch (e) {
        console.log(e);
    }
}
// 登录
export const LoginCode= async (data)=>{
    try {
        const res = await instance.post("/member/LoginCode",data);
        return res.data
    } catch (e) {
        console.log(e);
    }
}