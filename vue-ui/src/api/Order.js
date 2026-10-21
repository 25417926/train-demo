import instance from "@/api/interceptor";
import { message } from "ant-design-vue";

export const addOrderList = async (param) => {
    try {
        const res = await instance.post("/business/web/confirm-order/do-storage", {
            dailyTrainTicketId: param.dailyTrainTicketId,
            date: param.date,
            trainCode: param.trainCode,
            start: param.start,
            end: param.end,
            tickets: param.tickets,
            seat: param.seat,
            codeToken: param.CodeToken,
            code: param.code,
        });
        return res.data;
    } catch (e) {
        message.error(e.message);
        return { success: false, code: 500, message: e.message };
    }
};

export const getSendCode = async (email) => {
    try {
        const res = await instance.get("/auth/sso/SendCode/" + email);
        return res.data;
    } catch (e) {
        message.error("验证码发送失败，请稍后再试");
        return { success: false, code: 500, message: "验证码发送失败" };
    }
};

export const getImageCode = async (codeToken) => {
    try {
        const res = await instance.get("/business/kaptcha/image-code/" + codeToken);
        return res.data;
    } catch (e) {
        message.error("验证码加载失败，请重试");
        return { success: false, code: 500, message: "验证码加载失败" };
    }
};

export const queryOrderList = async (id) => {
    try {
        const res = await instance.get("/business/confirm-order/Query-Line-count/" + id);
        return res.data;
    } catch (e) {
        message.error(e.message);
        return { success: false, code: 500, message: e.message };
    }
};

export const getMyOrderList = async (param) => {
    try {
        const res = await instance.get("/business/web/confirm-order/Query-List", {
            params: {
                page: param.page,
                size: param.size
            }
        });
        return res.data;
    } catch (e) {
        message.error(e.message);
        return { success: false, code: 500, message: e.message };
    }
};
