function text(value) {
  return String(value || '').trim()
}

const SPLIT_PATTERN = /[、，,;；|/]+/

function splitCompound(value) {
  const parts = String(value || '').split(SPLIT_PATTERN).map((item) => String(item || '').trim())
  if (parts.length === 1 && !parts[0]) return []
  return parts
}

function pushPair(list, used, cropType, cropVariety) {
  const crop = text(cropType)
  const variety = text(cropVariety)
  if (!crop && !variety) return
  const key = `${crop}__${variety}`
  if (used.has(key)) return
  used.add(key)
  list.push({ cropType: crop, cropVariety: variety })
}

function pushAlignedText(list, used, cropText, varietyText) {
  const crops = splitCompound(cropText)
  const varieties = splitCompound(varietyText)
  const hasCrop = crops.some((x) => text(x))
  const hasVariety = varieties.some((x) => text(x))
  if (!hasCrop && !hasVariety) return
  const count = Math.max(crops.length, varieties.length, 1)
  for (let i = 0; i < count; i += 1) {
    const crop = text(crops[i] || '')
    const variety = text(varieties[i] || '')
    if (!crop && !variety) continue
    pushPair(list, used, crop, variety)
  }
}

function parseGroupsArray(input, list, used) {
  ;(Array.isArray(input) ? input : []).forEach((row) => {
    if (row && typeof row === 'object') {
      pushPair(
        list,
        used,
        row.cropType || row.cropName || row.categoryName || row.name || row.crop,
        row.cropVariety || row.varietyName || row.variety
      )
      return
    }
    pushPair(list, used, row, '')
  })
}

export function resolveCropVarietyGroups(payload) {
  if (!payload || typeof payload !== 'object') return []
  const list = []
  const used = new Set()

  parseGroupsArray(payload.cropVarietyGroups || payload.crops || payload.cropList, list, used)
  if (list.length) return list

  let cropsJsonRows = payload.cropsJson
  if (typeof cropsJsonRows === 'string' && text(cropsJsonRows)) {
    try {
      cropsJsonRows = JSON.parse(cropsJsonRows)
    } catch (error) {
      cropsJsonRows = null
    }
  }
  parseGroupsArray(cropsJsonRows, list, used)
  if (list.length) return list

  pushAlignedText(list, used, payload.currentCropType, payload.currentCropVariety)
  if (list.length) return list
  pushAlignedText(list, used, payload.cropType, payload.cropVariety)
  if (list.length) return list
  pushPair(list, used, payload.cropType, payload.varietyName)
  if (list.length) return list
  pushPair(list, used, payload.cropName, payload.varietyName)
  return list
}

export function resolveCropVarietyLabels(payload, maxCount = 0) {
  const labels = resolveCropVarietyGroups(payload)
    .map((row) => {
      const crop = text(row && row.cropType)
      const variety = text(row && row.cropVariety)
      if (crop && variety) return `${crop} · ${variety}`
      return crop || variety
    })
    .filter(Boolean)
  if (Number(maxCount) > 0) {
    return labels.slice(0, Number(maxCount))
  }
  return labels
}

export function formatCropVarietyPair(payload, emptyText = '未设置') {
  const labels = resolveCropVarietyLabels(payload)
  if (!labels.length) return String(emptyText || '')
  return labels.join('、')
}
