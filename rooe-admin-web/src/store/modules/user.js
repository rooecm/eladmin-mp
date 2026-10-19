import { login, getInfo, logout } from '@/api/login'
import { getToken, setToken, removeToken } from '@/utils/auth'
import { initializeSession } from '@/utils/session'

const user = {
  state: {
    token: getToken(),
    user: {},
    mustChangePassword: false,
    roles: [],
    // 第一次加载菜单时用到
    loadMenus: false
  },

  mutations: {
    SET_PASSWORD_CHANGE: (state, value) => {
      state.mustChangePassword = value
    },
    SET_TOKEN: (state, token) => {
      state.token = token
    },
    SET_USER: (state, user) => {
      state.user = user
    },
    SET_ROLES: (state, roles) => {
      state.roles = roles
    },
    SET_LOAD_MENUS: (state, loadMenus) => {
      state.loadMenus = loadMenus
    }
  },

  actions: {
    // 登录
    async Login({ commit }, userInfo) {
      const result = await login(userInfo.username, userInfo.password)
      if (result.status !== 'SUCCESS' || !result.accessToken) throw new Error('Login was not completed')
      setToken(result.accessToken)
      initializeSession(result.absoluteExpiresAt)
      commit('SET_TOKEN', result.accessToken)
      commit('SET_PASSWORD_CHANGE', result.mustChangePassword)
      commit('SET_LOAD_MENUS', true)
      return result
    },

    // 获取用户信息
    async GetInfo({ commit }) {
      const token = getToken()
      const res = await getInfo()
      // 请求返回前会话可能已切换，丢弃旧账号资料，避免覆盖新账号状态。
      if (!token || token !== getToken()) throw new Error('Session changed while loading user information')
      setUserInfo(res, commit)
      commit('SET_LOAD_MENUS', true)
      return res
    },
    // 登出
    ClearAuth({ commit }) {
      logOut(commit)
    },

    async LogOut({ commit }) {
      const token = getToken()
      try {
        await logout()
      } finally {
        // 网络失败也清理本地状态，但不影响等待期间建立的新会话。
        if (getToken() === token) logOut(commit)
      }
    },

    updateLoadMenus({ commit }) {
      commit('SET_LOAD_MENUS', false)
    }
  }
}

export const logOut = (commit) => {
  commit('SET_TOKEN', '')
  commit('SET_ROLES', [])
  commit('SET_USER', {})
  commit('SET_PASSWORD_CHANGE', false)
  commit('SET_LOAD_MENUS', false)
  removeToken()
}

export const setUserInfo = (res, commit) => {
  commit('SET_PASSWORD_CHANGE', !!res.mustChangePassword)
  // 空权限占位仅表示资料已加载，避免路由重复请求；不代表后端业务授权。
  commit('SET_ROLES', res.roles.length ? res.roles : ['ROLE_SYSTEM_DEFAULT'])
  commit('SET_USER', res.user)
}

export default user
