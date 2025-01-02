import instance from "@/api/interceptor";
import {message} from "ant-design-vue";

export const getStationList = async (param) => {
    try {
        const res = await instance.get("/business/station/Query-List",{
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
export const addStation = async (data) => {
    try {
        const res = await instance.post("/business/station/save",data);
        return res.data
    } catch (e) {
        console.log(e);
    }
}
export const deleteStation = async (data) => {
    try {
        const res = await instance.delete("/business/station/del/"+data);
        return res.data
    } catch (e) {
        console.log(e);
    }
}