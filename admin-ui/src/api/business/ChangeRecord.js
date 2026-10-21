import instance from "@/api/interceptor";
import {message} from "ant-design-vue";

export const getChangeRecordList = async (param) => {
    try {
        const res = await instance.get("/business/web/change/Query-List", {
            params: {
                page: param.page,
                size: param.size
            }
        });
        if (res.data.code === 200) {
            return res.data;
        } else {
            message.error(res.data.message);
        }
    } catch (e) {
        console.log(e);
    }
};
