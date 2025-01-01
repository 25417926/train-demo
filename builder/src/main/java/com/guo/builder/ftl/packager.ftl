import instance from "@/api/interceptor";
import {message} from "ant-design-vue";

export const get${Domain}List = async (param) => {
try {
const res = await instance.get("/business/${domain}/Query-List",{
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
export const add${Domain} = async (data) => {
try {
const res = await instance.post("/business/${domain}/save",data);
return res.data
} catch (e) {
console.log(e);
}
}
export const delete${Domain} = async (data) => {
try {
const res = await instance.delete("/business/${domain}/del/"+data);
return res.data
} catch (e) {
console.log(e);
}
}