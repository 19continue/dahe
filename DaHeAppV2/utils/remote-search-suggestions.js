import api from './request'

export const REMOTE_SEARCH_SUGGESTION_MIN_LENGTH = 1

const CACHE_TTL_MS = 45 * 1000
const MAX_CACHE_ENTRIES = 48
const cache = new Map()
const inflight = new Map()

function text(value) {
  return String(value || '').trim()
}

function normalizeKeyword(keyword) {
  return text(keyword)
    .normalize('NFKC')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim()
}

export function normalizeRemoteSearchKeyword(keyword) {
  return normalizeKeyword(keyword)
}

export function shouldFetchRemoteSearchSuggestions(keyword, minLength = REMOTE_SEARCH_SUGGESTION_MIN_LENGTH) {
  return normalizeKeyword(keyword).length >= Math.max(1, Number(minLength) || REMOTE_SEARCH_SUGGESTION_MIN_LENGTH)
}

function buildCacheKey(scene, keyword, limit) {
  return `${text(scene)}::${normalizeKeyword(keyword)}::${Number(limit) || 6}`
}

function pruneExpiredEntries(now = Date.now()) {
  Array.from(cache.entries()).forEach(([key, entry]) => {
    if (!entry || now - Number(entry.at || 0) >= CACHE_TTL_MS) {
      cache.delete(key)
    }
  })
}

function storeCacheEntry(cacheKey, rows) {
  pruneExpiredEntries()
  cache.set(cacheKey, { at: Date.now(), rows })
  if (cache.size <= MAX_CACHE_ENTRIES) {
    return
  }
  const keys = Array.from(cache.keys())
  const overflow = cache.size - MAX_CACHE_ENTRIES
  keys.slice(0, overflow).forEach((key) => {
    cache.delete(key)
  })
}

export function clearRemoteSearchSuggestionCache(scenePrefix = '') {
  const prefix = text(scenePrefix)
  if (!prefix) {
    cache.clear()
    inflight.clear()
    return
  }
  Array.from(cache.keys()).forEach((key) => {
    if (key.startsWith(`${prefix}::`)) {
      cache.delete(key)
    }
  })
  Array.from(inflight.keys()).forEach((key) => {
    if (key.startsWith(`${prefix}::`)) {
      inflight.delete(key)
    }
  })
}

export async function fetchRemoteSearchSuggestions(scene, keyword, limit = 6) {
  const safeScene = text(scene)
  const safeKeyword = normalizeKeyword(keyword)
  const safeLimit = Math.max(1, Math.min(10, Number(limit) || 6))
  if (!safeScene || !shouldFetchRemoteSearchSuggestions(safeKeyword)) {
    return []
  }
  const cacheKey = buildCacheKey(safeScene, safeKeyword, safeLimit)
  const now = Date.now()
  pruneExpiredEntries(now)
  const cached = cache.get(cacheKey)
  if (cached && now - cached.at < CACHE_TTL_MS) {
    return cached.rows
  }
  if (inflight.has(cacheKey)) {
    return inflight.get(cacheKey)
  }
  const promise = api
    .get('/miniapp/search/suggestions', {
      scene: safeScene,
      q: safeKeyword,
      limit: safeLimit
    })
    .then((rows) => {
      const result = Array.isArray(rows) ? rows : []
      storeCacheEntry(cacheKey, result)
      return result
    })
    .catch(() => [])
    .finally(() => {
      inflight.delete(cacheKey)
    })
  inflight.set(cacheKey, promise)
  return promise
}
