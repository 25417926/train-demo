import instance from "@/api/interceptor";
import {message} from "ant-design-vue";

export const getTrainList = async (param) => {
    try {
        const res = await instance.get("/business/train/Query-List",{
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
export const addTrain = async (data) => {
    try {
        const res = await instance.post("/business/train/save",data);
        return res.data
    } catch (e) {
        console.log(e);
    }
}
export const deleteTrain = async (data) => {
    try {
        const res = await instance.delete("/business/train/del/"+data);
        return res.data
    } catch (e) {
        console.log(e);
    }
}