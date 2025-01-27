import instance from "@/api/interceptor";
import {message} from "ant-design-vue";

export const getSk_TokenList = async (param) => {
    try {
        const res = await instance.get("/business/sk-token/Query-List", {
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
export const addSk_Token = async (data) => {
    try {
        const res = await instance.post("/business/sk-token/save", data);
        return res.data
    } catch (e) {
        console.log(e);
    }
}
export const deleteSk_Token = async (data) => {
    try {
        const res = await instance.delete("/business/sk-token/del/" + data);
        return res.data
    } catch (e) {
        console.log(e);
    }
}