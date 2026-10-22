import instance from "@/api/interceptor";


// 登录
    export const LoginCode = async (data) => {
        try {
            const res = await instance.post("/auth/sso/login",
                {
                    email: data.email,
                    password: data.password,
                    code: data.code,
                }
            );
            console.log(data)
            return res.data
        } catch (e) {
            console.log(e);
        }
    }
