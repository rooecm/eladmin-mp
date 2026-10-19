import router from './routers'
import store from '@/store'
import Config from '@/settings'
import NProgress from 'nprogress' // progress bar
import 'nprogress/nprogress.css' // progress bar style
import { getToken } from '@/utils/auth'
import { buildMenus } from '@/api/system/menu'
import { filterAsyncRouter } from '@/store/modules/permission'

NProgress.configure({ showSpinner: false })// NProgress Configuration

const whiteList = ['/login']// no redirect whitelist

router.beforeEach((to, from, next) => {
  if (to.meta.title) {
    document.title = to.meta.title + ' - ' + Config.title
  }
  NProgress.start()
  if (getToken()) {
    const proceed = () => {
      // 强制改密会话在加载业务菜单之前进入改密页，后端过滤器同步限制接口访问。
      if (store.state.user.mustChangePassword) {
        next(to.path === '/change-password' ? undefined : { path: '/change-password' })
        NProgress.done()
      } else if (to.path === '/login') {
        next({ path: '/' })
        NProgress.done()
      } else if (store.getters.loadMenus) {
        store.dispatch('updateLoadMenus')
        loadMenus(next, to)
      } else {
        next()
      }
    }
    if (store.getters.roles.length === 0) {
      store.dispatch('GetInfo').then(proceed).catch(() => {
        next(false)
        NProgress.done()
      })
    } else {
      proceed()
    }
  } else {
    /* has no token*/
    if (whiteList.indexOf(to.path) !== -1) { // 在免登录白名单，直接进入
      next()
    } else {
      next(`/login?redirect=${to.fullPath}`) // 否则全部重定向到登录页
      NProgress.done()
    }
  }
})

export const loadMenus = async(next, to) => {
  // 菜单与路由必须属于发起加载时的会话，账号切换后丢弃旧结果。
  const token = getToken()
  try {
    const res = await buildMenus()
    if (!token || getToken() !== token) {
      next(false)
      return
    }
    const sdata = JSON.parse(JSON.stringify(res))
    const rdata = JSON.parse(JSON.stringify(res))
    const sidebarRoutes = filterAsyncRouter(sdata)
    const rewriteRoutes = filterAsyncRouter(rdata)
    rewriteRoutes.push({ path: '*', redirect: '/404', hidden: true })

    await Promise.all([
      store.dispatch('GenerateRoutes', rewriteRoutes),
      store.dispatch('SetSidebarRouters', sidebarRoutes)
    ])
    if (getToken() !== token) {
      next(false)
      return
    }
    router.addRoutes(rewriteRoutes)
    next({ ...to, replace: true })
  } catch (e) {
    // 当前会话加载失败后恢复标记，允许下次导航重新加载菜单。
    if (getToken() === token) store.commit('SET_LOAD_MENUS', true)
    next(false)
  } finally {
    NProgress.done()
  }
}

router.afterEach(() => {
  NProgress.done() // finish progress bar
})
