

const SESSION_ORDER="SESSION_ORDER"
const SESSION_TICKET_PARAMS="SESSION_TICKET_PARAMS"
const Session_params= {
    namespaced: true,
        state: {
            order: window.SessionStorage.get(SESSION_ORDER) || null,
            params: window.SessionStorage.get(SESSION_TICKET_PARAMS) || null,
        },
        getters: {},
        mutations: {
            set_order(state, _order) {

                state.order = _order
                window.SessionStorage.set(SESSION_ORDER, _order)
            },
            set_params(state, _params) {
                state.params = _params
                window.SessionStorage.set(SESSION_TICKET_PARAMS, _params)
            },
        },
        actions: {},

    }

export default Session_params