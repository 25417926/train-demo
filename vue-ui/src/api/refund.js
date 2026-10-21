import instance from "@/api/interceptor";
import {message} from "ant-design-vue";

export const applyRefund = async (param) => {
    try {
        const res = await instance.post("/business/web/refund/apply", {
            orderId: param.orderId,
            reason: param.reason
        });
        return res.data;
    } catch (e) {
        message.error(e.message);
    }
};
