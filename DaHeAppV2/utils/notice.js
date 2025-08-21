const NOTICE_UNREAD_KEY = 'dahe.v2.noticeUnreadCount'
const NOTICE_UNREAD_EVENT = 'dahe:noticeUnreadChange'

function normalizeCount(value) {
  const next = Number(value || 0)
  if (!Number.isFinite(next) || next < 0) return 0
  return Math.floor(next)
}

export function getStoredNoticeUnreadCount() {
  return normalizeCount(uni.getStorageSync(NOTICE_UNREAD_KEY))
}

export function setStoredNoticeUnreadCount(value, emitEvent = true) {
  const next = normalizeCount(value)
  uni.setStorageSync(NOTICE_UNREAD_KEY, next)
  if (emitEvent && typeof uni.$emit === 'function') {
    uni.$emit(NOTICE_UNREAD_EVENT, next)
  }
  return next
}

export function onNoticeUnreadChange(handler) {
  if (typeof uni.$on !== 'function' || typeof handler !== 'function') return
  uni.$on(NOTICE_UNREAD_EVENT, handler)
}

export function offNoticeUnreadChange(handler) {
  if (typeof uni.$off !== 'function' || typeof handler !== 'function') return
  uni.$off(NOTICE_UNREAD_EVENT, handler)
}
