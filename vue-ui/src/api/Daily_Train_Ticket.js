import instance from "@/api/interceptor";
import {message} from "ant-design-vue";

export const getDaily_Train_TicketList = async (param) => {
    try {

        const res = await instance.get("/business/web/ticket/Query-List", {
            params: {
                page: param.page,
                size: param.size,
                trainCode: param.code,
                date: param.date,
                start: param.start,
                end: param.end
            }
        });
        console.table(res.data.content.list)
        if (res.data.code === 200) {
            return res.data

        } else {
            message.error(res.data.message)
        }
    } catch (e) {
        console.log(e);
    }

}