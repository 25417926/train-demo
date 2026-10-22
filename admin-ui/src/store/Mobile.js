import {LocalStorageUtil} from "@/storage/Local-Storage";

const mobile = 'mobile'
const Token = 'token'
export const Mobile = {
    state: {
        mobile: LocalStorageUtil.getItem(mobile),
        token: LocalStorageUtil.getItem(Token),
    },
    mutations: {
        set_mobile(state, _mobile) {
            state.mobile = _mobile
            LocalStorageUtil.setItem(mobile, _mobile)
        },
        set_token(state, token) {
            state.token = token
            LocalStorageUtil.setItem(Token, token)
        },
        clean(){
            LocalStorageUtil.removeItem(mobile)
            LocalStorageUtil.removeItem(Token)
        }
    }
}
export default Mobile