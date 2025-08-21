const ELDER_MODE_KEY = 'dahe.v2.elderMode'

export function isElderMode() {
  return uni.getStorageSync(ELDER_MODE_KEY) === '1'
}

export function setElderMode(enabled) {
  const next = enabled ? '1' : '0'
  uni.setStorageSync(ELDER_MODE_KEY, next)
  uni.$emit('dahe:elder-mode-changed', enabled)
  return enabled
}

export function toggleElderMode() {
  return setElderMode(!isElderMode())
}

export function getElderModeKey() {
  return ELDER_MODE_KEY
}

