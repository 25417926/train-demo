import instance from "@/api/interceptor";
import {message} from "ant-design-vue";

export const gettrain_seatList = async (param) => {
    try {
        const res = await instance.get("/business/train-seat/Query-List", {
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
export const addtrain_seat = async (data) => {
    try {
        const res = await instance.post("/business/train-seat/save", data);
        return res.data
    } catch (e) {
        console.log(e);
    }
}
export const deletetrain_seat = async (data) => {
    try {
        const res = await instance.delete("/business/train-seat/del/" + data);
        return res.data
    } catch (e) {
        console.log(e);
    }
}