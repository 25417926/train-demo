const SESSION_PARAMS = 'SESSION_PARAMS'
const SESSION_ORDER = 'SESSION_ORDER'
export const Session_params = {
    state: {
        order: window.SessionStorage.get(SESSION_ORDER),
        params: window.SessionStorage.get(SESSION_PARAMS),
    },
    mutations: {
        set_order(state, _order) {
            state.order = _order
            window.SessionStorage.set(SESSION_ORDER, _order)
        },
        set_params(state, _params) {
            state.params = _params
            window.SessionStorage.set(SESSION_PARAMS, _params)
        },

    }
}
export default Session_params