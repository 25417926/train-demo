import instance from "@/api/interceptor";
import {message} from "ant-design-vue";

export const addOrderList = async (param) => {
    try{
        const res =await instance.post("/business/web/confirm-order/do-storage", {
            dailyTrainTicketId: param.dailyTrainTicketId,
            date: param.date,
            trainCode: param.trainCode,
            start: param.start,
            end: param.end,
            tickets: param.tickets,
            seat: param.seat,
            codeToken: param.CodeToken,
            code: param.code,
            // lineNumber: param.value
        })
        console.log(param)
        return res.data
    }catch (e){
        message.error(e.message)
    }
}
export const getSendCode = async (email)=>{
    try{
        const res = await instance.get("/auth/sso/SendCode/"+email
        )
        return res.data
    }catch (e){
        message.error("验证码发送失败，请稍后再试")
    }
}
export const queryOrderList =async (id) =>{
    try{
        const res = await instance.get("/business/confirm-order/Query-Line-count/"+id)
        return res.data
    }catch (e){
        message.error(e.message)
    }
}