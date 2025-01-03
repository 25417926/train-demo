import instance from "@/api/interceptor";
import {message} from "ant-design-vue";

export const getDaily_Train_CarriageList = async (param) => {
    try {
        const res = await instance.get("/business/daily-train-carriage/Query-List", {
            params: {
                page: param.page,
                size: param.size,
                trainCode: param.code,
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
export const addDaily_Train_Carriage = async (data) => {
    try {
        const res = await instance.post("/business/daily-train-carriage/save", data);
        return res.data
    } catch (e) {
        console.log(e);
    }
}
export const deleteDaily_Train_Carriage = async (data) => {
    try {
        const res = await instance.delete("/business/daily-train-carriage/del/" + data);
        return res.data
    } catch (e) {
        console.log(e);
    }
}