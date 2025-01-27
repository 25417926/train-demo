import instance from "@/api/interceptor";
import {message} from "ant-design-vue";

export const getConfirm_OrderList = async (param) => {
try {
const res = await instance.get("/business/confirm-order/Query-List",{
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
export const addConfirm_Order = async (data) => {
try {
const res = await instance.post("/business/confirm-order/save",data);
return res.data
} catch (e) {
console.log(e);
}
}
export const deleteConfirm_Order = async (data) => {
try {
const res = await instance.delete("/business/confirm-order/del/"+data);
return res.data
} catch (e) {
console.log(e);
}
}