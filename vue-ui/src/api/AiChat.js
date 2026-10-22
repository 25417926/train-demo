import instance from "@/api/interceptor";


// ai接口调用
export const getChatList = async (data) => {
    try {
        const res = await instance.post("/member/Passenger/chat",
            data
        );
        console.log(data)
        return res.data
    } catch (e) {
        console.log(e);
    }
}
