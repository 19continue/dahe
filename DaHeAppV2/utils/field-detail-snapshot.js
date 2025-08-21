const STORAGE_PREFIX = 'dahe.v2.fieldDetailSnapshot:'
const SNAPSHOT_TTL_MS = 30 * 60 * 1000

function normalizeId(value) {
  return String(value == null ? '' : value).trim()
}

function storageKey(id) {
  const safeId = normalizeId(id)
  return safeId ? `${STORAGE_PREFIX}${safeId}` : ''
}

function sanitizeField(field) {
  if (!field || typeof field !== 'object') return null
  const id = normalizeId(field.id)
  if (!id) return null
  return {
    id,
    name: field.name || '',
    areaMu: field.areaMu,
    cropType: field.cropType || '',
    cropVariety: field.cropVariety || '',
    cropVarietyGroups: Array.isArray(field.cropVarietyGroups) ? field.cropVarietyGroups : [],
    province: field.province || '',
    city: field.city || '',
    district: field.district || '',
    township: field.township || '',
    formattedAddress: field.formattedAddress || '',
    locationDesc: field.locationDesc || '',
    locationLat: field.locationLat,
    locationLng: field.locationLng,
    status: field.status || '',
    stage: field.stage || '',
    coverImageUrl: field.coverImageUrl || '',
    imageUrl: field.imageUrl || '',
    coverUrl: field.coverUrl || '',
    cover: field.cover || '',
    photoUrl: field.photoUrl || '',
    banner: field.banner || '',
    picture: field.picture || '',
    thumbUrl: field.thumbUrl || '',
    fileUrl: field.fileUrl || '',
    images: field.images || [],
    imageList: field.imageList || [],
    photos: field.photos || [],
    currentMatched: field.currentMatched,
    relationText: field.relationText || '',
    distanceText: field.distanceText || '',
    distanceMeters: field.distanceMeters,
    cachedAt: Date.now()
  }
}

export function writeFieldDetailSnapshot(field) {
  const snapshot = sanitizeField(field)
  if (!snapshot) return
  const key = storageKey(snapshot.id)
  if (!key) return
  try {
    uni.setStorageSync(key, snapshot)
  } catch (error) {
    // ignore
  }
}

export function readFieldDetailSnapshot(id) {
  const key = storageKey(id)
  if (!key) return null
  try {
    const raw = uni.getStorageSync(key)
    if (!raw || typeof raw !== 'object') return null
    const cachedAt = Number(raw.cachedAt || 0)
    if (!Number.isFinite(cachedAt) || Date.now() - cachedAt > SNAPSHOT_TTL_MS) {
      uni.removeStorageSync(key)
      return null
    }
    return raw
  } catch (error) {
    return null
  }
}
