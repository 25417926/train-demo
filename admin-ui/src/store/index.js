import { createStore } from 'vuex'
import Mobile from '@/store/Mobile'
import Session_params from "@/store/Session_params";

export default createStore({
    modules: {
        Mobile: {
            ...Mobile,
            namespaced: true
        },
        Session_params: {
            ...Session_params,
            namespaced: true
        }
    }
})
