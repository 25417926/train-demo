import instance from "@/api/interceptor";
import {message} from "ant-design-vue";

export const payOrder = async (param) => {
    try {
        const res = await instance.post("/business/web/payment/pay", {
            orderId: param.orderId,
            amount: param.amount,
            payChannel: param.payChannel
        });
        return res.data;
    } catch (e) {
        message.error(e.message);
    }
};

export const queryPaymentByOrder = async (orderId) => {
    try {
        const res = await instance.get("/business/web/payment/query/" + orderId);
        return res.data;
    } catch (e) {
        message.error(e.message);
    }
};
