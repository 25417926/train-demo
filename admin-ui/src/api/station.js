import instance from "@/api/interceptor";
import {message} from "ant-design-vue";

export const getPassengerList = async (param) => {
    try {
        const res = await instance.get("/member/Passenger/Query-List",{
            params:{
                page:param.page,
                size:param.size
            }
        });
        if(res.data.code === 200) {
            return res.data

        }else{
            message.error(res.data.message)
        }
    } catch (e) {
        console.log(e);
    }
}
export const addPassenger = async (data) => {
    try {
        const res = await instance.post("/member/Passenger/save",data);
        return res.data
    } catch (e) {
        console.log(e);
    }
}
export const deletePassenger = async (data) => {
    try {
        const res = await instance.delete("/member/Passenger/del/"+data);
        return res.data
    } catch (e) {
        console.log(e);
    }
}