import { createStore } from 'vuex'
import Session_params from "@/store/Session_params";

export default createStore({
    modules: {
        Session_params: {
            ...Session_params,
            namespaced: true
        }
    }
})
