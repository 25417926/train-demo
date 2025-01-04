import instance from "@/api/interceptor";
import {message} from "ant-design-vue";

export const getDaily_Train_TicketList = async (param) => {
try {
const res = await instance.get("/business/daily-train-ticket/Query-List",{
params:{
page:param.page,
size:param.size
}
});
if(res.data.code === 200) {
return res.data

}else{
message.error(res.data.message)
}
} catch (e) {
console.log(e);
}
}
export const addDaily_Train_Ticket = async (data) => {
try {
const res = await instance.post("/business/daily-train-ticket/save",data);
return res.data
} catch (e) {
console.log(e);
}
}
export const deleteDaily_Train_Ticket = async (data) => {
try {
const res = await instance.delete("/business/daily-train-ticket/del/"+data);
return res.data
} catch (e) {
console.log(e);
}
}