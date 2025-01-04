
import {LocalStorageUtil} from "@/storage/Local-Storage";
const MOBILE ='mobile'
const TOKEN ='token'
const Mobile= {
    namespaced: true,
    state: {
            token: LocalStorageUtil.getItem(TOKEN) || null,
            mobile: window.sessionStorage.getItem(MOBILE) || null,
        },
        getters: {},
        mutations: {
            set_token(state, _token) {
                state.token = _token
                LocalStorageUtil.setItem(TOKEN, _token)
            },
            set_mobile(state, _mobile) {
                state.mobile = _mobile
                window.sessionStorage.setItem(MOBILE, _mobile)
            },
        },
        actions: {},

}
export default Mobile