import instance from "@/api/interceptor";
import {message} from "ant-design-vue";

export const addOrderList = async (param) => {
    try{
        const res =await instance.post("/business/web/confirm-order/do-storage", {
            dailyTrainTicketId: param.dailyTrainTicketId,
            date: param.date,
            trainCode: param.trainCode,
            start: param.start,
            end: param.end,
            tickets: param.tickets,
            seat: param.seat,
            // imageCodeToken: param.value,
            // imageCode: param.value,
            // lineNumber: param.value
        })
        return res.data
    }catch (e){
        message.error(e.message)
    }
}
// axios.post("/business/confirm-order/do", {
//     dailyTrainTicketId: dailyTrainTicket.id,
//     date: dailyTrainTicket.date,
//     trainCode: dailyTrainTicket.trainCode,
//     start: dailyTrainTicket.start,
//     end: dailyTrainTicket.end,
//     tickets: tickets.value,
//     imageCodeToken: imageCodeToken.value,
//     imageCode: imageCode.value,
//     lineNumber: lineNumber.value
// }).then((response) => {
//     let data = response.data;
//     if (data.success) {
//         // notification.success({description: "下单成功！"});
//         visible.value = false;
//         imageCodeModalVisible.value = false;
//         lineModalVisible.value = true;
//         confirmOrderId.value = data.content;
//         queryLineCount();
//     } else {
//         notification.error({description: data.message});
//     }
// });