import instance from "@/api/interceptor";
import { message } from "ant-design-vue";

const buildIdempotencyKey = () => {
    const random = window.Tool && window.Tool.uuid ? window.Tool.uuid(16) : String(Date.now());
    return "WL" + random;
};

export const applyWaitlist = async (param) => {
    try {
        const res = await instance.post(
            "/business/web/waitlist/apply",
            {
                date: param.date,
                trainCode: param.trainCode,
                start: param.start,
                end: param.end,
                seatType: param.seatType,
                passengerId: param.passengerId
            },
            { headers: { "Idempotency-Key": buildIdempotencyKey() } }
        );
        return res.data;
    } catch (e) {
        message.error("候补申请失败，请稍后重试");
        return { success: false, code: 500, message: "候补申请失败" };
    }
};

export const cancelWaitlist = async (applicationId) => {
    try {
        const res = await instance.post(`/business/web/waitlist/${applicationId}/cancel`);
        return res.data;
    } catch (e) {
        message.error("取消候补失败，请稍后重试");
        return { success: false, code: 500, message: "取消候补失败" };
    }
};

export const getMyWaitlist = async (param) => {
    try {
        const res = await instance.get("/business/web/waitlist/mine", {
            params: {
                page: param.page,
                size: param.size,
                status: param.status
            }
        });
        return res.data;
    } catch (e) {
        return { success: false, code: 500, message: "查询候补失败" };
    }
};
