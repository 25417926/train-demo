import instance from "@/api/interceptor";
import { message } from "ant-design-vue";

export const getWaitlistList = async (param) => {
    try {
        const res = await instance.get("/business/waitlist/Query-List", {
            params: {
                page: param.page,
                size: param.size,
                date: param.date,
                trainCode: param.trainCode,
                start: param.start,
                end: param.end,
                seatType: param.seatType,
                status: param.status
            }
        });
        if (res.data.code === 200) {
            return res.data;
        }
        message.error(res.data.message);
    } catch (e) {
        console.log(e);
    }
};

export const cancelWaitlist = async (applicationId) => {
    try {
        const res = await instance.post(`/business/waitlist/${applicationId}/cancel`);
        return res.data;
    } catch (e) {
        console.log(e);
    }
};
