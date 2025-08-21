let installed = false

export function installMiniappUpdateGuard(handlers = {}) {
  if (installed) return
  // #ifdef MP-WEIXIN
  if (typeof wx === 'undefined' || typeof wx.getUpdateManager !== 'function') {
    return
  }
  installed = true
  const manager = wx.getUpdateManager()
  manager.onCheckForUpdate((res) => {
    if (res && res.hasUpdate) {
      if (typeof handlers.onChecking === 'function') {
        handlers.onChecking()
      }
      return
    }
    if (typeof handlers.onIdle === 'function') {
      handlers.onIdle()
    }
  })
  manager.onUpdateReady(() => {
    if (typeof handlers.onReady === 'function') {
      handlers.onReady(manager)
    }
  })
  manager.onUpdateFailed(() => {
    if (typeof handlers.onFailed === 'function') {
      handlers.onFailed()
    }
  })
  // #endif
}
