import instance from "@/api/interceptor";
import {message} from "ant-design-vue";

export const applyChange = async (param) => {
    try {
        const res = await instance.post("/business/web/change/apply", {
            originalOrderId: param.originalOrderId,
            newOrder: param.newOrder
        });
        return res.data;
    } catch (e) {
        message.error(e.message);
    }
};
