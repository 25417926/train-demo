import instance from "@/api/interceptor";
import {message} from "ant-design-vue";

export const getDaily_TrainList = async (param) => {
    try {
        const res = await instance.get("/business/daily-train/Query-List", {
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
export const addDaily_Train = async (data) => {
    try {
        const res = await instance.post("/business/daily-train/save", data);
        return res.data
    } catch (e) {
        console.log(e);
    }
}
export const deleteDaily_Train = async (data) => {
    try {
        const res = await instance.delete("/business/daily-train/del/" + data);
        return res.data
    } catch (e) {
        console.log(e);
    }
}
export const addGenDaily = async (data) => {
    try {
        const res = await instance.get("/business/daily-train/gen-daily/"+data);
        return res.data
    } catch (e) {
        console.log(e);
    }
}