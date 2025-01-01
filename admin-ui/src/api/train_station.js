import instance from "@/api/interceptor";
import {message} from "ant-design-vue";

export const getTrain_StationList = async (param) => {
    try {
        const res = await instance.get("/business/train-station/Query-List",{
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
export const addTrain_Station = async (data) => {
    try {
        const res = await instance.post("/business/train-station/save",data);
        return res.data
    } catch (e) {
        console.log(e);
    }
}
export const deleteTrain_Station = async (data) => {
    try {
        const res = await instance.delete("/business/train-station/del/"+data);
        return res.data
    } catch (e) {
        console.log(e);
    }
}