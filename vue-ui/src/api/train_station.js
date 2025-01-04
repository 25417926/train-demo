import instance from "@/api/interceptor";
import {message} from "ant-design-vue";


export const getTrainList = async () => {
    try {
        const res = await instance.get("/business/web/train/Query-All",);
        if(res.data.code === 200) {
            return res.data
        }else{
            message.error(res.data.message)
        }
    } catch (e) {
        console.log(e);
    }
}
export const getStationList = async () => {
    try {
        const res = await instance.get("/business/web/station/Query-All",);
        if(res.data.code === 200) {
            return res.data
        }else{
            message.error(res.data.message)
        }
    } catch (e) {
        console.log(e);
    }
}