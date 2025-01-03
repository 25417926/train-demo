import instance from "@/api/interceptor";
import {message} from "ant-design-vue";

export const getDaily_Train_SeatList = async (param) => {
    try {
        const res = await instance.get("/business/daily-train-seat/Query-List", {
            params: {
                page: param.page,
                size: param.size,
                trainCode: param.code
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
export const addDaily_Train_Seat = async (data) => {
    try {
        const res = await instance.post("/business/daily-train-seat/save", data);
        return res.data
    } catch (e) {
        console.log(e);
    }
}
export const deleteDaily_Train_Seat = async (data) => {
    try {
        const res = await instance.delete("/business/daily-train-seat/del/" + data);
        return res.data
    } catch (e) {
        console.log(e);
    }
}