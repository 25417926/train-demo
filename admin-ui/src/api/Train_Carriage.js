import instance from "@/api/interceptor";
import {message} from "ant-design-vue";

export const getTrain_CarriageList = async (param) => {
    try {
        const res = await instance.get("/business/train-carriage/Query-List", {
            params: {
                page: param.page,
                size: param.size,
                trainCode: param.trainCode
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
export const addTrain_Carriage = async (data) => {
    try {
        const res = await instance.post("/business/train-carriage/save", data);
        return res.data
    } catch (e) {
        console.log(e);
    }
}
export const deleteTrain_Carriage = async (data) => {
    try {
        const res = await instance.delete("/business/train-carriage/del/" + data);
        return res.data
    } catch (e) {
        console.log(e);
    }
}