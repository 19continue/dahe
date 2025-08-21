function text(value) {
  return String(value || '').trim()
}

const TYPE_PRIORITY = {
  田块: 120,
  批次: 116,
  作物: 112,
  品种: 110,
  乡镇: 108,
  位置: 104,
  姓名: 114,
  手机: 109,
  账号: 107,
  昵称: 106
}

export function readSearchValue(context) {
  if (context && Object.prototype.hasOwnProperty.call(context, 'value')) {
    return text(context.value)
  }
  if (context && context.detail && Object.prototype.hasOwnProperty.call(context.detail, 'value')) {
    return text(context.detail.value)
  }
  if (typeof context === 'string') {
    return text(context)
  }
  return ''
}

export function splitSearchSegments(value, maxCount = 4) {
  return text(value)
    .split(/[\s,\uFF0C\u3001\/|_+\-]+/u)
    .map((item) => text(item))
    .filter((item) => item.length >= 2)
    .slice(0, Math.max(1, Number(maxCount) || 4))
}

function suggestionScore(keyword, label, typeLabel, order) {
  const safeKeyword = text(keyword).toLowerCase()
  const safeLabel = text(label).toLowerCase()
  const typeScore = TYPE_PRIORITY[typeLabel] || 100
  if (!safeKeyword || !safeLabel) {
    return typeScore - order
  }
  let score = typeScore * 10
  if (safeLabel === safeKeyword) {
    score += 1000
  } else if (safeLabel.startsWith(safeKeyword)) {
    score += 720
  } else {
    const index = safeLabel.indexOf(safeKeyword)
    if (index >= 0) {
      score += Math.max(240, 520 - index * 18)
    }
  }
  score += Math.max(0, 80 - safeLabel.length * 2)
  score -= order
  return score
}

export function buildKeywordSuggestions(keyword, sourceList, limit = 6) {
  const safeKeyword = text(keyword).toLowerCase()
  if (!safeKeyword) return []
  const out = []
  const used = new Set()
  ;(Array.isArray(sourceList) ? sourceList : []).forEach((item, index) => {
    const label = text(item && item.label)
    const typeLabel = text(item && item.typeLabel)
    const value = text((item && item.value) || label)
    if (!label || !typeLabel || !value) return
    if (!label.toLowerCase().includes(safeKeyword)) return
    const key = `${typeLabel}:${value}`
    if (used.has(key)) return
    used.add(key)
    out.push({
      key,
      label,
      value,
      typeLabel,
      score: suggestionScore(safeKeyword, label, typeLabel, index)
    })
  })
  return out
    .sort((a, b) => {
      if (b.score !== a.score) return b.score - a.score
      if (a.label.length !== b.label.length) return a.label.length - b.label.length
      return a.label.localeCompare(b.label, 'zh-Hans-CN')
    })
    .slice(0, Math.max(1, Number(limit) || 6))
    .map(({ score, ...item }) => item)
}
