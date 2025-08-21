const STORAGE_KEY = 'dahe.v2.searchHistory'
const MAX_SCENES = 8
const MAX_ITEMS_PER_SCENE = 6
const HISTORY_TTL_MS = 45 * 24 * 60 * 60 * 1000

function text(value) {
  return String(value || '').trim()
}

function safeNow() {
  return Date.now()
}

function normalizeKeyword(keyword) {
  return text(keyword)
    .normalize('NFKC')
    .replace(/\s+/g, ' ')
    .trim()
}

function readStorage() {
  try {
    const raw = uni.getStorageSync(STORAGE_KEY)
    return raw && typeof raw === 'object' ? raw : {}
  } catch (error) {
    return {}
  }
}

function writeStorage(value) {
  try {
    uni.setStorageSync(STORAGE_KEY, value)
  } catch (error) {
    // Ignore quota errors and keep runtime stable.
  }
}

function pruneSceneItems(items, limit = MAX_ITEMS_PER_SCENE, now = safeNow()) {
  const safeLimit = Math.max(1, Number(limit) || MAX_ITEMS_PER_SCENE)
  return (Array.isArray(items) ? items : [])
    .filter((item) => item && text(item.value) && now - Number(item.at || 0) < HISTORY_TTL_MS)
    .sort((left, right) => Number(right.at || 0) - Number(left.at || 0))
    .slice(0, safeLimit)
}

function pruneStorage(store, now = safeNow()) {
  const nextStore = {}
  Object.keys(store || {}).forEach((scene) => {
    const safeScene = text(scene)
    if (!safeScene) return
    const rows = pruneSceneItems(store[safeScene], MAX_ITEMS_PER_SCENE, now)
    if (rows.length) {
      nextStore[safeScene] = rows
    }
  })
  const rankedScenes = Object.keys(nextStore)
    .map((scene) => ({
      scene,
      at: Math.max(...nextStore[scene].map((item) => Number(item.at || 0)), 0)
    }))
    .sort((left, right) => right.at - left.at)
    .slice(0, MAX_SCENES)
  const prunedStore = {}
  rankedScenes.forEach((item) => {
    prunedStore[item.scene] = nextStore[item.scene]
  })
  return prunedStore
}

export function readSearchHistory(scene, limit = MAX_ITEMS_PER_SCENE) {
  const safeScene = text(scene)
  if (!safeScene) return []
  const store = pruneStorage(readStorage())
  writeStorage(store)
  return pruneSceneItems(store[safeScene], limit).map((item) => item.value)
}

export function pushSearchHistory(scene, keyword, limit = MAX_ITEMS_PER_SCENE) {
  const safeScene = text(scene)
  const safeKeyword = normalizeKeyword(keyword)
  if (!safeScene || !safeKeyword) {
    return readSearchHistory(safeScene, limit)
  }
  const now = safeNow()
  const store = pruneStorage(readStorage(), now)
  const sceneRows = pruneSceneItems(store[safeScene], limit, now)
    .filter((item) => item.value !== safeKeyword)
  sceneRows.unshift({
    value: safeKeyword,
    at: now
  })
  store[safeScene] = pruneSceneItems(sceneRows, limit, now)
  writeStorage(pruneStorage(store, now))
  return readSearchHistory(safeScene, limit)
}

export function clearSearchHistory(scene = '') {
  const safeScene = text(scene)
  if (!safeScene) {
    writeStorage({})
    return []
  }
  const store = readStorage()
  delete store[safeScene]
  writeStorage(pruneStorage(store))
  return []
}

export function removeSearchHistoryItem(scene, keyword, limit = MAX_ITEMS_PER_SCENE) {
  const safeScene = text(scene)
  const safeKeyword = normalizeKeyword(keyword)
  if (!safeScene || !safeKeyword) {
    return readSearchHistory(safeScene, limit)
  }
  const now = safeNow()
  const store = pruneStorage(readStorage(), now)
  const sceneRows = pruneSceneItems(store[safeScene], limit, now)
    .filter((item) => normalizeKeyword(item.value) !== safeKeyword)
  if (sceneRows.length) {
    store[safeScene] = sceneRows
  } else {
    delete store[safeScene]
  }
  writeStorage(pruneStorage(store, now))
  return readSearchHistory(safeScene, limit)
}
