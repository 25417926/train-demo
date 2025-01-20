import instance from "@/api/interceptor";


// 登录
export const LoginCode = async (data) => {
    try {
        const res = await instance.post("/member/LoginCode",
            {
                mobile: data.mobile,
                code: data.code,
                password: data.password,
                tokenCode: data.TokenCode
            }
        );
        console.log(data)
        return res.data
    } catch (e) {
        console.log(e);
    }
}
