import Vue from 'vue'

import Cookies from 'js-cookie'

import 'normalize.css/normalize.css'

import Element from 'element-ui'

// 数据字典
import dict from './components/Dict'

// 权限指令
import checkPer from '@/utils/permission'
import permission from './components/Permission'
import './assets/styles/element-variables.scss'

// global css
import './assets/styles/index.scss'

import {startSessionActivity} from '@/utils/session'
import {logout} from '@/api/login'
import App from './App'
import store from './store'
import router from './router/routers'

import './assets/icons' // icon
import './router/index' // permission control

Vue.use(checkPer)
Vue.use(permission)
Vue.use(dict)
Vue.use(Element, {
  size: Cookies.get('size') || 'small' // set element-ui default size
})

Vue.config.productionTip = false

startSessionActivity({
  logout,
  onWarning: () => Element.Notification.warning({
    title: '登录即将过期',
    message: '长时间未操作，即将退出；继续操作可保持登录。'
  }),
  onLogout: async tokenChanged => {
    if (tokenChanged) {
      location.reload()
      return
    }
    await store.dispatch('ClearAuth')
    router.replace('/login')
  }
})

new Vue({
  el: '#app',
  router,
  store,
  render: h => h(App)
})

