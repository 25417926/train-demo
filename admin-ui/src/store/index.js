import { createStore } from 'vuex'
import {LocalStorageUtil} from '@/storage/Local-Storage'

const Mobile ='mobile'
const Token ='token'
export default createStore({

  modules: {
    MemberToken:{
      state: {
        token:LocalStorageUtil.getItem(Token)||null,
        mobile:window.sessionStorage.getItem(Mobile)||null,
      },
      getters: {
      },
      mutations: {
        set_token(state,_token){
          state.token=_token
          LocalStorageUtil.setItem(Token,_token)
        },
        set_mobile(state,_mobile){
          state.mobile=_mobile
          window.sessionStorage.setItem(Mobile,_mobile)
        },
      },
      actions: {
      },
    }
  }
})
