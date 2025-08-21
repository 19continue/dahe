import api from './request'
const MINIAPP_AMAP_PREFIX = '/miniapp/amap'
const AUTO_WEATHER_CACHE_KEY = 'dahe.v2.autoWeatherSnapshot'
const AUTO_WEATHER_CACHE_AT_KEY = 'dahe.v2.autoWeatherSnapshotAt'
let autoWeatherMemorySnapshot = null
let autoWeatherMemoryAt = 0
let autoWeatherInFlight = null
const DEFAULT_PROVINCES = [
  '北京市', '天津市', '上海市', '重庆市',
  '河北省', '山西省', '辽宁省', '吉林省', '黑龙江省',
  '江苏省', '浙江省', '安徽省', '福建省', '江西省', '山东省',
  '河南省', '湖北省', '湖南省', '广东省', '海南省',
  '四川省', '贵州省', '云南省', '陕西省', '甘肃省', '青海省',
  '内蒙古自治区', '广西壮族自治区', '西藏自治区', '宁夏回族自治区', '新疆维吾尔自治区',
  '香港特别行政区', '澳门特别行政区', '台湾省'
]
const DEFAULT_PROVINCE_ADCODE = {
  北京市: '110000', 天津市: '120000', 上海市: '310000', 重庆市: '500000',
  河北省: '130000', 山西省: '140000', 辽宁省: '210000', 吉林省: '220000', 黑龙江省: '230000',
  江苏省: '320000', 浙江省: '330000', 安徽省: '340000', 福建省: '350000', 江西省: '360000', 山东省: '370000',
  河南省: '410000', 湖北省: '420000', 湖南省: '430000', 广东省: '440000', 海南省: '460000',
  四川省: '510000', 贵州省: '520000', 云南省: '530000', 陕西省: '610000', 甘肃省: '620000', 青海省: '630000',
  内蒙古自治区: '150000', 广西壮族自治区: '450000', 西藏自治区: '540000', 宁夏回族自治区: '640000', 新疆维吾尔自治区: '650000',
  香港特别行政区: '810000', 澳门特别行政区: '820000', 台湾省: '710000'
}
const PROVINCE_ADCODE_MAP = new Map()
const CITY_ADCODE_MAP = new Map()
const DISTRICT_ADCODE_MAP = new Map()
Object.keys(DEFAULT_PROVINCE_ADCODE).forEach((name) => {
  const adcode = DEFAULT_PROVINCE_ADCODE[name]
  if (!name || !adcode) return
  PROVINCE_ADCODE_MAP.set(name, adcode)
})



export function getCurrentLocation() {
  return new Promise((resolve, reject) => {
    uni.getLocation({
      type: 'gcj02',
      isHighAccuracy: true,
      success: (res) => {
        resolve({
          latitude: res.latitude,
          longitude: res.longitude,
          accuracy: res.accuracy
        })
      },
      fail: (err) => {
        const errMsg = String((err && err.errMsg) || '')
        if (errMsg.includes('requiredPrivateInfos')) {
          reject(new Error('小程序未声明定位隐私接口，请重新编译后重试'))
          return
        }
        reject(err)
      }
    })
  })
}

function readAutoWeatherCache(ttlMs) {
  const safeTtl = Number(ttlMs || 0)
  if (!Number.isFinite(safeTtl) || safeTtl <= 0) return null
  const now = Date.now()
  if (autoWeatherMemorySnapshot && now - Number(autoWeatherMemoryAt || 0) <= safeTtl) {
    return autoWeatherMemorySnapshot
  }
  const cachedAt = Number(uni.getStorageSync(AUTO_WEATHER_CACHE_AT_KEY) || 0)
  const cachedSnapshot = uni.getStorageSync(AUTO_WEATHER_CACHE_KEY)
  if (!cachedAt || !cachedSnapshot || typeof cachedSnapshot !== 'object') {
    return null
  }
  if (now - cachedAt > safeTtl) {
    return null
  }
  autoWeatherMemorySnapshot = cachedSnapshot
  autoWeatherMemoryAt = cachedAt
  return cachedSnapshot
}

function writeAutoWeatherCache(snapshot) {
  if (!snapshot || typeof snapshot !== 'object') return
  const cachedAt = Date.now()
  autoWeatherMemorySnapshot = snapshot
  autoWeatherMemoryAt = cachedAt
  uni.setStorageSync(AUTO_WEATHER_CACHE_KEY, snapshot)
  uni.setStorageSync(AUTO_WEATHER_CACHE_AT_KEY, cachedAt)
}

export function clearAutoWeatherSnapshotCache() {
  autoWeatherMemorySnapshot = null
  autoWeatherMemoryAt = 0
  uni.removeStorageSync(AUTO_WEATHER_CACHE_KEY)
  uni.removeStorageSync(AUTO_WEATHER_CACHE_AT_KEY)
}

export async function getAutoWeatherSnapshot(options = {}) {
  const forceRefresh = !!(options && options.forceRefresh)
  const cacheTtlMs = Number((options && options.cacheTtlMs) || 0)
  const presetLocation = options && typeof options === 'object' ? options.location : null
  if (!forceRefresh && !presetLocation) {
    const cached = readAutoWeatherCache(cacheTtlMs)
    if (cached) {
      return cached
    }
    if (autoWeatherInFlight) {
      return autoWeatherInFlight
    }
  }

  const requestTask = (async () => {
    const latitude = Number(presetLocation && presetLocation.latitude)
    const longitude = Number(presetLocation && presetLocation.longitude)
    const loc = Number.isFinite(latitude) && Number.isFinite(longitude)
      ? {
          latitude,
          longitude
        }
      : await getCurrentLocation()
    const proxy = await api.get(`${MINIAPP_AMAP_PREFIX}/weather/snapshot`, {
      longitude: Number(loc.longitude),
      latitude: Number(loc.latitude)
    })
    if (!proxy || (!proxy.formattedAddress && !proxy.adcode && !proxy.weather)) {
      throw new Error('weather snapshot not available')
    }
    const snapshot = {
      latitude: loc.latitude,
      longitude: loc.longitude,
      adcode: proxy.adcode || '',
      province: proxy.province || '',
      city: proxy.city || '',
      district: proxy.district || '',
      township: proxy.township || '',
      formattedAddress: proxy.formattedAddress || '',
      weather: proxy.weather || '',
      temperature: proxy.temperature || '',
      humidity: proxy.humidity || '',
      windDirection: proxy.windDirection || '',
      windPower: proxy.windPower || '',
      reportTime: proxy.reportTime || ''
    }
    writeAutoWeatherCache(snapshot)
    return snapshot
  })()

  autoWeatherInFlight = requestTask
  try {
    return await requestTask
  } finally {
    if (autoWeatherInFlight === requestTask) {
      autoWeatherInFlight = null
    }
  }
}
export async function fetchAddressTips(params = {}) {
  const keywords = String(params.keywords || '').trim()
  if (!keywords) return []
  const payload = {
    keywords,
    city: params.city || undefined,
    longitude: params.longitude != null ? Number(params.longitude) : undefined,
    latitude: params.latitude != null ? Number(params.latitude) : undefined,
    cityLimit: params.cityLimit ? true : undefined,
    limit: params.limit || 20
  }
  const rows = await api.get(`${MINIAPP_AMAP_PREFIX}/address/tips`, payload)
  return Array.isArray(rows) ? rows : []
}
export async function normalizeAddressByLocation(longitude, latitude) {
  const lng = Number(longitude)
  const lat = Number(latitude)
  if (Number.isNaN(lng) || Number.isNaN(lat)) {
    throw new Error('invalid location')
  }
  return api.get(`${MINIAPP_AMAP_PREFIX}/address/regeo`, {
    longitude: lng,
    latitude: lat
  })
}
export async function normalizeAddressByText(address, city) {
  const raw = String(address || '').trim()
  if (!raw) {
    throw new Error('missing address')
  }
  return api.get(`${MINIAPP_AMAP_PREFIX}/address/geocode`, {
    address: raw,
    city: city || undefined
  })
}
function mapRegionRows(rows) {
  return (Array.isArray(rows) ? rows : [])
    .map((row) => {
      const value = String((row && (row.value || row.label)) || '').trim()
      const label = String((row && (row.label || row.value)) || '').trim() || value
      const adcode = String((row && row.adcode) || '').trim()
      const level = String((row && row.level) || '').trim()
      const parentName = String((row && row.parentName) || '').trim()
      if (!value) return null
      return { value, label, adcode, level, parentName }
    })
    .filter(Boolean)
}

function mapMetaRegionRows(rows) {
  return (Array.isArray(rows) ? rows : [])
    .map((x) => String(x || '').trim())
    .filter(Boolean)
    .map((text) => ({ value: text, label: text, adcode: '', level: '', parentName: '' }))
}

function rememberRegionAdcodes(path, rows) {
  const targetMap = path === `${MINIAPP_AMAP_PREFIX}/regions/provinces`
    ? PROVINCE_ADCODE_MAP
    : (path === `${MINIAPP_AMAP_PREFIX}/regions/cities`
      ? CITY_ADCODE_MAP
      : (path === `${MINIAPP_AMAP_PREFIX}/regions/districts` ? DISTRICT_ADCODE_MAP : null))
  if (!targetMap || !Array.isArray(rows)) return
  rows.forEach((row) => {
    const value = String((row && row.value) || '').trim()
    const adcode = String((row && row.adcode) || '').trim()
    if (!value || !adcode) return
    targetMap.set(value, adcode)
  })
}

function buildRegionRequestParams(path, params = {}) {
  const out = { ...(params || {}) }
  if (path === `${MINIAPP_AMAP_PREFIX}/regions/cities`) {
    const province = String(out.province || '').trim()
    if (!/^\d{6}$/.test(province)) {
      const adcode = PROVINCE_ADCODE_MAP.get(province)
      if (adcode) {
        out.province = adcode
      }
    }
    return out
  }
  if (path === `${MINIAPP_AMAP_PREFIX}/regions/districts`) {
    const city = String(out.city || '').trim()
    if (!/^\d{6}$/.test(city)) {
      const adcode = CITY_ADCODE_MAP.get(city)
      if (adcode) {
        out.city = adcode
      }
    }
    return out
  }
  if (path === `${MINIAPP_AMAP_PREFIX}/regions/townships`) {
    const district = String(out.district || '').trim()
    if (!/^\d{6}$/.test(district)) {
      const adcode = DISTRICT_ADCODE_MAP.get(district)
      if (adcode) {
        out.district = adcode
      }
    }
    return out
  }
  return out
}

async function fetchMetaRegionRows(path, params = {}) {
  try {
    const rows = await api.get(path, params)
    return mapMetaRegionRows(rows)
  } catch (e) {
    return []
  }
}

export async function fetchProvinceOptions(keyword) {
  try {
    const path = `${MINIAPP_AMAP_PREFIX}/regions/provinces`
    const params = buildRegionRequestParams(path, {
      keyword: keyword || undefined,
      limit: 80
    })
    const rows = await api.get(path, params)
    rememberRegionAdcodes(path, rows)
    const mapped = mapRegionRows(rows)
    if (mapped.length) return mapped
  } catch (e) {}
  const metaRows = await fetchMetaRegionRows('/miniapp/meta/options/provinces', { keyword: keyword || undefined })
  if (metaRows.length) return metaRows
  return DEFAULT_PROVINCES
    .filter((x) => {
      const k = String(keyword || '').trim()
      return !k || x.includes(k)
    })
    .map((x) => ({ value: x, label: x, adcode: DEFAULT_PROVINCE_ADCODE[x] || '', level: 'province', parentName: '' }))
}

export async function fetchCityOptions(province) {
  if (!province) return []
  const rawParams = {
    province: province || undefined,
    limit: 120
  }
  const path = `${MINIAPP_AMAP_PREFIX}/regions/cities`
  const params = buildRegionRequestParams(path, rawParams)
  try {
    const rows = await api.get(path, params)
    rememberRegionAdcodes(path, rows)
    const mapped = mapRegionRows(rows)
    if (mapped.length) return mapped
  } catch (e) {}
  let metaRows = await fetchMetaRegionRows('/miniapp/meta/options/cities', {
    province: province || undefined
  })
  if (metaRows.length) return metaRows
  metaRows = await fetchMetaRegionRows('/miniapp/meta/options/cities')
  return metaRows
}

export async function fetchDistrictOptions(city) {
  if (!city) return []
  const rawParams = {
    city: city || undefined,
    limit: 180
  }
  const path = `${MINIAPP_AMAP_PREFIX}/regions/districts`
  const params = buildRegionRequestParams(path, rawParams)
  try {
    const rows = await api.get(path, params)
    rememberRegionAdcodes(path, rows)
    const mapped = mapRegionRows(rows)
    if (mapped.length) return mapped
  } catch (e) {}
  let metaRows = await fetchMetaRegionRows('/miniapp/meta/options/districts', {
    city: city || undefined
  })
  if (metaRows.length) return metaRows
  metaRows = await fetchMetaRegionRows('/miniapp/meta/options/districts')
  return metaRows
}

export async function fetchTownshipOptions(district) {
  if (!district) return []
  const rawParams = {
    district: district || undefined,
    limit: 260
  }
  const path = `${MINIAPP_AMAP_PREFIX}/regions/townships`
  const params = buildRegionRequestParams(path, rawParams)
  try {
    const rows = await api.get(path, params)
    const mapped = mapRegionRows(rows)
    if (mapped.length) return mapped
  } catch (e) {}
  let metaRows = await fetchMetaRegionRows('/miniapp/meta/options/townships', {
    district: district || undefined
  })
  if (metaRows.length) return metaRows
  metaRows = await fetchMetaRegionRows('/miniapp/meta/options/townships')
  return metaRows
}

export function formatWeatherLabel(snapshot) {
  if (!snapshot) return '天气未获取'
  const weather = snapshot.weather || '未知天气'
  const temp = snapshot.temperature ? `${snapshot.temperature}°C` : '--'
  return `${weather} ${temp}`
}

export function formatLocationLabel(snapshot) {
  if (!snapshot) return '位置未获取'
  const arr = [snapshot.city, snapshot.district, snapshot.township].filter(Boolean)
  if (arr.length) return arr.join(' ')
  return snapshot.formattedAddress || '位置未获取'
}

export function haversineDistanceMeters(lat1, lng1, lat2, lng2) {
  const toRad = (d) => (d * Math.PI) / 180
  const R = 6371000
  const dLat = toRad(lat2 - lat1)
  const dLng = toRad(lng2 - lng1)
  const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) *
    Math.sin(dLng / 2) * Math.sin(dLng / 2)
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
  return R * c
}

export function formatDistance(meters) {
  if (meters == null || Number.isNaN(Number(meters))) return '--'
  const m = Number(meters)
  if (m < 1000) return `${Math.round(m)}m`
  return `${(m / 1000).toFixed(1)}km`
}
