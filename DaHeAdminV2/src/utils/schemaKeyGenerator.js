import { pinyin as pinyinPro } from 'pinyin-pro'

const PHRASE_TOKEN_MAP = [
  ['作物品种组合', 'crop variety combo'],
  ['作物品种', 'crop variety'],
  ['作物分类', 'crop category'],
  ['玉米', 'maize'],
  ['大豆', 'soybean'],
  ['小麦', 'wheat'],
  ['水稻', 'rice'],
  ['棉花', 'cotton'],
  ['油菜', 'rapeseed'],
  ['花生', 'peanut'],
  ['马铃薯', 'potato'],
  ['甘薯', 'sweet potato'],
  ['番茄', 'tomato'],
  ['西红柿', 'tomato'],
  ['黄瓜', 'cucumber'],
  ['辣椒', 'pepper'],
  ['西瓜', 'watermelon'],
  ['苹果', 'apple'],
  ['柑橘', 'citrus'],
  ['葡萄', 'grape'],
  ['草莓', 'strawberry'],
  ['郑单', 'zhengdan'],
  ['中黄', 'zhonghuang'],
  ['先玉', 'xianyu'],
  ['登海', 'denghai'],
  ['金苑', 'jinyuan'],
  ['流程模板', 'process template'],
  ['流程步骤', 'process step'],
  ['步骤参数', 'step param'],
  ['参数模板', 'param template'],
  ['动态参数', 'dynamic param'],
  ['农事记录', 'farm record'],
  ['作业位置', 'work location'],
  ['操作人员', 'operator'],
  ['操作员', 'operator'],
  ['批次编号', 'batch code'],
  ['批次名称', 'batch name'],
  ['批次日期', 'batch date'],
  ['批次状态', 'batch status'],
  ['检测结果', 'test result'],
  ['检测项目', 'test item'],
  ['检测时间', 'test time'],
  ['检测日期', 'test date'],
  ['检测数量', 'test amount'],
  ['发芽数量', 'germination amount'],
  ['发芽率', 'germination rate'],
  ['发芽天数', 'germination days'],
  ['含水率', 'moisture rate'],
  ['天气发布时间', 'weather publish time'],
  ['天气情况', 'weather'],
  ['空气湿度', 'humidity'],
  ['土壤湿度', 'soil humidity'],
  ['降雨量', 'rainfall'],
  ['降水量', 'rainfall'],
  ['风向', 'wind direction'],
  ['风力', 'wind scale'],
  ['风速', 'wind speed'],
  ['最低温度', 'min temperature'],
  ['最高温度', 'max temperature'],
  ['平均温度', 'avg temperature'],
  ['温度', 'temperature'],
  ['田块名称', 'field name'],
  ['田块面积', 'field area'],
  ['详细地址', 'detail address'],
  ['省市区', 'province city district'],
  ['区县', 'district county'],
  ['乡镇', 'town'],
  ['生长阶段', 'growth stage'],
  ['播种阶段', 'sowing stage'],
  ['收获阶段', 'harvest stage'],
  ['开始时间', 'start time'],
  ['结束时间', 'end time'],
  ['开始日期', 'start date'],
  ['结束日期', 'end date'],
  ['创建时间', 'created time'],
  ['更新时间', 'updated time'],
  ['默认值', 'default value'],
  ['占位提示', 'placeholder'],
  ['控制台开通', 'console enabled'],
  ['控制台权限', 'console permission'],
  ['审核状态', 'review status'],
  ['手机号码', 'mobile'],
  ['手机号', 'mobile'],
  ['用户名', 'username'],
  ['昵称', 'nickname'],
  ['真实姓名', 'real name'],
  ['排序', 'sort order'],
  ['序号', 'sort order'],
  ['施肥', 'fertilization'],
  ['追肥', 'topdressing'],
  ['基肥', 'base fertilizer'],
  ['尿素', 'urea'],
  ['复合肥', 'compound fertilizer'],
  ['有机肥', 'organic fertilizer'],
  ['氮肥', 'nitrogen fertilizer'],
  ['磷肥', 'phosphate fertilizer'],
  ['钾肥', 'potash fertilizer'],
  ['农药', 'pesticide'],
  ['杀虫剂', 'insecticide'],
  ['杀菌剂', 'fungicide'],
  ['除草剂', 'herbicide'],
  ['灌溉', 'irrigation'],
  ['滴灌', 'drip irrigation'],
  ['喷灌', 'sprinkler irrigation'],
  ['漫灌', 'flood irrigation'],
  ['除草', 'weeding'],
  ['病虫害', 'pest control'],
  ['采收', 'harvest'],
  ['播种', 'sowing'],
  ['移栽', 'transplanting'],
  ['整地', 'land preparation'],
  ['数量', 'amount'],
  ['位置', 'location'],
  ['类型', 'type'],
  ['名称', 'name'],
  ['日期', 'date'],
  ['时间', 'time'],
  ['备注', 'remark'],
  ['状态', 'status'],
  ['阶段', 'stage'],
  ['地址', 'address'],
  ['地点', 'site'],
  ['作物', 'crop'],
  ['品种', 'variety'],
  ['批次', 'batch'],
  ['检测', 'test'],
  ['图片', 'image'],
  ['照片', 'photo'],
  ['附件', 'attachment'],
  ['开关', 'switch'],
  ['必填', 'required'],
  ['可选', 'optional'],
  ['公斤', 'kilogram'],
  ['千克', 'kilogram'],
  ['毫克', 'milligram'],
  ['克', 'gram'],
  ['吨', 'ton'],
  ['毫升', 'milliliter'],
  ['升', 'liter'],
  ['公里', 'kilometer'],
  ['厘米', 'centimeter'],
  ['毫米', 'millimeter'],
  ['公顷', 'hectare'],
  ['百分比', 'percent'],
  ['亩', 'mu']
]

const FREE_TRANSLATE_API_TIMEOUT_MS = 1600
const FREE_TRANSLATE_CACHE = new Map()
const FREE_TRANSLATE_PENDING = new Map()
const FREE_TRANSLATE_MAX_CACHE_SIZE = 600
const FREE_TRANSLATE_PERSIST_KEY = 'dahe.schema.translate.cache.v1'
const CUSTOM_PHRASE_PERSIST_KEY = 'dahe.schema.custom.phrase.v1'
const CUSTOM_PHRASE_MAX_SIZE = 300
const CUSTOM_PHRASE_MAX_TEXT_LENGTH = 60

let freeTranslatePersistLoaded = false
let customPhrasePersistLoaded = false
let SORTED_PHRASES = []

function buildPhraseRows(entries) {
  return (Array.isArray(entries) ? entries : [])
    .map((item) => {
      const source = normalizeText(item && item[0])
      const target = normalizeText(item && item[1])
      if (!source || !target) return null
      return {
        source,
        targetTokens: String(target || '')
          .split(/[^A-Za-z0-9]+/)
          .filter(Boolean)
          .map((token) => token.toLowerCase())
      }
    })
    .filter((item) => item && item.source && Array.isArray(item.targetTokens) && item.targetTokens.length > 0)
    .sort((a, b) => b.source.length - a.source.length)
}

export function normalizeText(value) {
  return String(value == null ? '' : value).trim()
}

function canUseStorage() {
  return typeof window !== 'undefined' && window && window.localStorage
}

function safeReadStorage(key) {
  if (!canUseStorage()) return ''
  try {
    return String(window.localStorage.getItem(key) || '')
  } catch (error) {
    return ''
  }
}

function safeWriteStorage(key, value) {
  if (!canUseStorage()) return
  try {
    window.localStorage.setItem(key, value)
  } catch (error) {
    // ignore storage write errors
  }
}

function normalizePhraseEntries(entries) {
  const rows = []
  const used = new Set()
  ;(Array.isArray(entries) ? entries : []).forEach((item) => {
    const source = normalizeText(item && item[0]).slice(0, CUSTOM_PHRASE_MAX_TEXT_LENGTH)
    const target = normalizeText(item && item[1]).slice(0, CUSTOM_PHRASE_MAX_TEXT_LENGTH * 2)
    if (!source || !target || used.has(source)) return
    used.add(source)
    rows.push([source, target])
  })
  return rows.slice(0, CUSTOM_PHRASE_MAX_SIZE)
}

function readGlobalCustomPhraseEntries() {
  if (typeof window === 'undefined') return []
  const direct = window.__DAHE_SCHEMA_KEY_DICT__
  if (Array.isArray(direct)) {
    return normalizePhraseEntries(direct)
  }
  if (direct && typeof direct === 'object') {
    return normalizePhraseEntries(Object.entries(direct))
  }
  return []
}

function loadCustomPhraseEntries() {
  if (customPhrasePersistLoaded) return
  customPhrasePersistLoaded = true
  const globalRows = readGlobalCustomPhraseEntries()
  if (globalRows.length) {
    SORTED_PHRASES = buildPhraseRows([...globalRows, ...PHRASE_TOKEN_MAP])
    return
  }
  const raw = safeReadStorage(CUSTOM_PHRASE_PERSIST_KEY)
  if (!raw) return
  try {
    const parsed = JSON.parse(raw)
    const customRows = normalizePhraseEntries(parsed)
    if (!customRows.length) return
    SORTED_PHRASES = buildPhraseRows([...customRows, ...PHRASE_TOKEN_MAP])
  } catch (error) {
    // ignore invalid persisted dictionary
  }
}

function ensureSortedPhrases() {
  if (SORTED_PHRASES.length) return
  loadCustomPhraseEntries()
  if (!SORTED_PHRASES.length) {
    SORTED_PHRASES = buildPhraseRows(PHRASE_TOKEN_MAP)
  }
}

export function getCustomPhraseEntries() {
  loadCustomPhraseEntries()
  const customRows = SORTED_PHRASES
    .map((item) => [item.source, (item.targetTokens || []).join(' ')])
    .filter((item) => {
      const source = item[0]
      return !PHRASE_TOKEN_MAP.some((base) => normalizeText(base && base[0]) === source)
    })
  return customRows
}

export function setCustomPhraseEntries(entries) {
  const customRows = normalizePhraseEntries(entries)
  SORTED_PHRASES = buildPhraseRows([...customRows, ...PHRASE_TOKEN_MAP])
  safeWriteStorage(CUSTOM_PHRASE_PERSIST_KEY, JSON.stringify(customRows))
  return getCustomPhraseEntries()
}

export function clearCustomPhraseEntries() {
  SORTED_PHRASES = buildPhraseRows(PHRASE_TOKEN_MAP)
  if (canUseStorage()) {
    try {
      window.localStorage.removeItem(CUSTOM_PHRASE_PERSIST_KEY)
    } catch (error) {
      // ignore
    }
  }
}

function isChineseChar(char) {
  return /[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]/.test(char)
}

function hasChineseText(text) {
  return /[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]/.test(String(text || ''))
}

function hasPhraseAt(source, cursor) {
  ensureSortedPhrases()
  for (let i = 0; i < SORTED_PHRASES.length; i += 1) {
    if (source.startsWith(SORTED_PHRASES[i].source, cursor)) {
      return true
    }
  }
  return false
}

function convertUnknownChineseChunk(chunk) {
  const text = String(chunk || '')
  if (!text) return ''
  const libTokens = toEnglishTokensFromTranslatedText(convertChineseByPinyinPro(text))
  if (libTokens.length) {
    return libTokens.join(' ')
  }
  const tokens = []
  for (let i = 0; i < text.length; i += 1) {
    const char = text[i]
    const code = char.codePointAt(0)
    if (Number.isFinite(code)) {
      tokens.push(`u${code.toString(16)}`)
    }
  }
  return tokens.join(' ')
}

function normalizeSourceText(value) {
  return normalizeText(value)
    .replace(/[\u3000]/g, ' ')
    .replace(/[，,。；;：:、]/g, ' ')
    .replace(/[和与及]/g, ' ')
    .replace(/[（）()【】\[\]{}<>《》]/g, ' ')
    .replace(/[\/\\|]/g, ' ')
    .replace(/[＋+]/g, ' plus ')
    .replace(/[％%]/g, ' percent ')
    .replace(/℃/g, ' celsius ')
    .replace(/°/g, ' degree ')
    .replace(/㎡/g, ' square meter ')
    .replace(/\s+/g, ' ')
    .trim()
}

function pushAsciiTokens(buffer, text) {
  String(text || '')
    .split(/[^A-Za-z0-9]+/)
    .map((item) => item.trim().toLowerCase())
    .filter(Boolean)
    .forEach((item) => buffer.push(item))
}

function tokenizeToEnglishDetailed(value) {
  ensureSortedPhrases()
  const source = normalizeSourceText(value)
  if (!source) {
    return {
      tokens: [],
      unknownChunkCount: 0,
      chineseChunkCount: 0
    }
  }

  const tokens = []
  let unknownChunkCount = 0
  let chineseChunkCount = 0
  let cursor = 0
  while (cursor < source.length) {
    const current = source[cursor]
    if (/\s/.test(current)) {
      cursor += 1
      continue
    }

    if (/[A-Za-z0-9_]/.test(current)) {
      let end = cursor + 1
      while (end < source.length && /[A-Za-z0-9_]/.test(source[end])) {
        end += 1
      }
      pushAsciiTokens(tokens, source.slice(cursor, end))
      cursor = end
      continue
    }

    let matched = null
    for (let i = 0; i < SORTED_PHRASES.length; i += 1) {
      const phrase = SORTED_PHRASES[i]
      if (source.startsWith(phrase.source, cursor)) {
        matched = phrase
        break
      }
    }
    if (matched) {
      tokens.push(...matched.targetTokens)
      chineseChunkCount += 1
      cursor += matched.source.length
      continue
    }

    if (isChineseChar(current)) {
      let end = cursor
      while (end < source.length) {
        const char = source[end]
        if (!isChineseChar(char)) break
        if (hasPhraseAt(source, end)) break
        end += 1
      }
      if (end === cursor) {
        end = cursor + 1
      }
      const unknownChunk = source.slice(cursor, end)
      const pinyinToken = convertUnknownChineseChunk(unknownChunk)
      if (pinyinToken) {
        pushAsciiTokens(tokens, pinyinToken)
      }
      chineseChunkCount += 1
      unknownChunkCount += 1
      cursor = end
      continue
    }
    cursor += 1
  }

  return {
    tokens,
    unknownChunkCount,
    chineseChunkCount
  }
}

function tokenizeToEnglish(value) {
  return tokenizeToEnglishDetailed(value).tokens
}

function decodeBasicEntities(text) {
  return String(text || '')
    .replace(/&amp;/g, '&')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
}

function normalizeTranslatedText(text) {
  return decodeBasicEntities(text)
    .replace(/[_/]+/g, ' ')
    .replace(/[，,。；;：:、]/g, ' ')
    .replace(/[（）()【】\[\]{}<>《》]/g, ' ')
    .replace(/[\u3000]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
}

function toEnglishTokensFromTranslatedText(text) {
  const buffer = []
  pushAsciiTokens(buffer, normalizeTranslatedText(text))
  return buffer
}

function convertChineseByPinyinPro(text) {
  const source = String(text || '')
  if (!source || typeof pinyinPro !== 'function') return ''
  try {
    return normalizeTranslatedText(
      pinyinPro(source, {
        toneType: 'none',
        nonZh: 'spaced',
        v: true
      })
    )
  } catch (error) {
    return ''
  }
}

function loadPersistedTranslateCache() {
  if (freeTranslatePersistLoaded) return
  freeTranslatePersistLoaded = true
  const raw = safeReadStorage(FREE_TRANSLATE_PERSIST_KEY)
  if (!raw) return
  try {
    const parsed = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object') return
    const entries = Object.entries(parsed).slice(-FREE_TRANSLATE_MAX_CACHE_SIZE)
    entries.forEach(([key, value]) => {
      const source = normalizeText(key)
      const translated = normalizeTranslatedText(value)
      if (!source) return
      FREE_TRANSLATE_CACHE.set(source, translated)
    })
  } catch (error) {
    // ignore invalid cache data
  }
}

function persistTranslateCache() {
  if (!canUseStorage()) return
  const entries = Array.from(FREE_TRANSLATE_CACHE.entries()).slice(-FREE_TRANSLATE_MAX_CACHE_SIZE)
  const payload = {}
  entries.forEach(([key, value]) => {
    if (!normalizeText(key)) return
    payload[key] = normalizeTranslatedText(value)
  })
  safeWriteStorage(FREE_TRANSLATE_PERSIST_KEY, JSON.stringify(payload))
}

function buildGoogleTranslateUrl(text) {
  return `https://translate.googleapis.com/translate_a/single?client=gtx&sl=zh-CN&tl=en&dt=t&q=${encodeURIComponent(text)}`
}

function parseGoogleTranslateResponse(payload) {
  if (!Array.isArray(payload) || !Array.isArray(payload[0])) return ''
  return payload[0]
    .map((row) => (Array.isArray(row) ? String(row[0] || '') : ''))
    .join(' ')
    .trim()
}

function buildMyMemoryUrl(text) {
  return `https://api.mymemory.translated.net/get?q=${encodeURIComponent(text)}&langpair=zh-CN|en`
}

function parseMyMemoryResponse(payload) {
  if (!payload || typeof payload !== 'object') return ''
  return String((payload.responseData && payload.responseData.translatedText) || '').trim()
}

const FREE_TRANSLATE_API_PROVIDERS = [
  {
    id: 'google-translate',
    buildUrl: buildGoogleTranslateUrl,
    parseResponse: parseGoogleTranslateResponse
  },
  {
    id: 'mymemory',
    buildUrl: buildMyMemoryUrl,
    parseResponse: parseMyMemoryResponse
  }
]

async function fetchJsonWithTimeout(url, timeoutMs = FREE_TRANSLATE_API_TIMEOUT_MS) {
  if (typeof fetch !== 'function') return null
  let timer = null
  let signal
  if (typeof AbortController === 'function') {
    const controller = new AbortController()
    timer = setTimeout(() => controller.abort(), Math.max(300, Number(timeoutMs) || FREE_TRANSLATE_API_TIMEOUT_MS))
    signal = controller.signal
  }
  try {
    const response = await fetch(url, {
      method: 'GET',
      mode: 'cors',
      cache: 'no-store',
      signal
    })
    if (!response.ok) return null
    const text = await response.text()
    if (!text) return null
    try {
      return JSON.parse(text)
    } catch (error) {
      return null
    }
  } catch (error) {
    return null
  } finally {
    if (timer) clearTimeout(timer)
  }
}

async function translateChineseTextByFreeApi(text) {
  const source = normalizeText(text)
  if (!source || !hasChineseText(source)) return ''
  loadPersistedTranslateCache()
  if (FREE_TRANSLATE_CACHE.has(source)) {
    return FREE_TRANSLATE_CACHE.get(source)
  }
  if (FREE_TRANSLATE_PENDING.has(source)) {
    return FREE_TRANSLATE_PENDING.get(source)
  }

  const pending = (async () => {
    for (let i = 0; i < FREE_TRANSLATE_API_PROVIDERS.length; i += 1) {
      const provider = FREE_TRANSLATE_API_PROVIDERS[i]
      try {
        const payload = await fetchJsonWithTimeout(provider.buildUrl(source))
        const translated = normalizeTranslatedText(provider.parseResponse(payload))
        if (translated && !hasChineseText(translated)) {
          if (FREE_TRANSLATE_CACHE.size >= FREE_TRANSLATE_MAX_CACHE_SIZE) {
            const firstKey = FREE_TRANSLATE_CACHE.keys().next().value
            if (firstKey) FREE_TRANSLATE_CACHE.delete(firstKey)
          }
          FREE_TRANSLATE_CACHE.set(source, translated)
          persistTranslateCache()
          return translated
        }
      } catch (error) {
        // ignore and continue fallback providers
      }
    }
    if (FREE_TRANSLATE_CACHE.size >= FREE_TRANSLATE_MAX_CACHE_SIZE) {
      const firstKey = FREE_TRANSLATE_CACHE.keys().next().value
      if (firstKey) FREE_TRANSLATE_CACHE.delete(firstKey)
    }
    FREE_TRANSLATE_CACHE.set(source, '')
    persistTranslateCache()
    return ''
  })()

  FREE_TRANSLATE_PENDING.set(source, pending)
  try {
    return await pending
  } finally {
    FREE_TRANSLATE_PENDING.delete(source)
  }
}

function shouldUseRemoteTranslate(label, detail) {
  const source = normalizeText(label)
  if (!source || !hasChineseText(source)) return false
  if (source.length > 64) return false
  if (!detail) return true
  const unknown = Number(detail.unknownChunkCount || 0)
  if (!Array.isArray(detail.tokens) || !detail.tokens.length) return true
  return unknown > 0
}

async function generateMachineKeyAsync(label, fallbackPrefix, index = 1, usedSet) {
  const detail = tokenizeToEnglishDetailed(label)
  let tokens = detail.tokens

  if (shouldUseRemoteTranslate(label, detail)) {
    const translatedText = await translateChineseTextByFreeApi(normalizeSourceText(label))
    const remoteTokens = toEnglishTokensFromTranslatedText(translatedText)
    if (remoteTokens.length) {
      tokens = remoteTokens
    }
  }

  const base = toLowerCamel(tokens, fallbackPrefix, index)
  return ensureUniqueMachineKey(base, usedSet)
}

function toLowerCamel(tokens, fallbackPrefix, fallbackIndex) {
  const rows = Array.isArray(tokens) ? tokens.filter(Boolean) : []
  const fallback = `${fallbackPrefix}${Math.max(1, Number(fallbackIndex || 1))}`
  if (!rows.length) return fallback

  const head = rows[0]
  const tail = rows.slice(1).map((item) => item.charAt(0).toUpperCase() + item.slice(1))
  let raw = `${head}${tail.join('')}`.replace(/[^A-Za-z0-9]/g, '')
  if (!raw) return fallback
  if (!/^[A-Za-z]/.test(raw)) {
    raw = `${fallbackPrefix}${raw}`
  }
  return raw
}

export function normalizeManualMachineKey(rawValue, fallbackPrefix, fallbackIndex) {
  const fallback = `${fallbackPrefix}${Math.max(1, Number(fallbackIndex || 1))}`
  let value = normalizeText(rawValue).replace(/[^A-Za-z0-9_]/g, '')
  if (!value) return fallback
  if (!/^[A-Za-z]/.test(value)) {
    value = `${fallbackPrefix}${value.replace(/^_+/, '')}`
  }
  return value || fallback
}

export function ensureUniqueMachineKey(baseKey, usedSet) {
  const normalized = normalizeText(baseKey)
  if (!usedSet) return normalized
  let candidate = normalized
  let counter = 2
  while (usedSet.has(candidate)) {
    candidate = `${normalized}${counter}`
    counter += 1
  }
  usedSet.add(candidate)
  return candidate
}

export function generateFieldKey(label, index = 1, usedSet) {
  const base = toLowerCamel(tokenizeToEnglish(label), 'field', index)
  return ensureUniqueMachineKey(base, usedSet)
}

export function generateOptionValue(label, index = 1, usedSet) {
  const base = toLowerCamel(tokenizeToEnglish(label), 'option', index)
  return ensureUniqueMachineKey(base, usedSet)
}

export async function generateFieldKeyAsync(label, index = 1, usedSet) {
  return generateMachineKeyAsync(label, 'field', index, usedSet)
}

export async function generateOptionValueAsync(label, index = 1, usedSet) {
  return generateMachineKeyAsync(label, 'option', index, usedSet)
}
