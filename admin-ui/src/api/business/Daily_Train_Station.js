import instance from "@/api/interceptor";
import {message} from "ant-design-vue";

export const getDaily_Train_StationList = async (param) => {
    try {
        const res = await instance.get("/business/daily-train-station/Query-List", {
            params: {
                page: param.page,
                size: param.size,
                code: param.code,
                date: param.date
            }
        });
        if (res.data.code === 200) {
            return res.data

        } else {
            message.error(res.data.message)
        }
    } catch (e) {
        console.log(e);
    }
}
export const addDaily_Train_Station = async (data) => {
    try {
        const res = await instance.post("/business/daily-train-station/save", data);
        return res.data
    } catch (e) {
        console.log(e);
    }
}
export const deleteDaily_Train_Station = async (data) => {
    try {
        const res = await instance.delete("/business/daily-train-station/del/" + data);
        return res.data
    } catch (e) {
        console.log(e);
    }
}