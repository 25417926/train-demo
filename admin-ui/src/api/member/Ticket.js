import instance from "@/api/interceptor";
import {message} from "ant-design-vue";

export const getTicketList = async (param) => {
    try {
        const res = await instance.get("/member/admin/ticket/Query-List", {
            params: {
                page: param.page,
                size: param.size
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
