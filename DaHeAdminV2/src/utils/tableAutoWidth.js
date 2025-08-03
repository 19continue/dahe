function textUnits(value) {
  const text = String(value == null ? '' : value)
  let units = 0
  for (let i = 0; i < text.length; i += 1) {
    const code = text.charCodeAt(i)
    units += code > 255 ? 2 : 1
  }
  return units
}

function estimateWidthByText(value) {
  return Math.ceil(textUnits(value) * 7.4 + 28)
}

function percentile(values, p) {
  const list = Array.isArray(values) ? values.filter((x) => Number.isFinite(x)).sort((a, b) => a - b) : []
  if (!list.length) return 0
  if (list.length === 1) return list[0]
  const index = Math.max(0, Math.min(list.length - 1, Math.round((list.length - 1) * p)))
  return list[index]
}

function normalizeNumber(value, fallback) {
  const n = Number(value)
  return Number.isFinite(n) ? n : fallback
}

export function buildAdaptiveWidths(input) {
  const columns = Array.isArray(input && input.columns) ? input.columns : []
  const rows = Array.isArray(input && input.rows) ? input.rows : []
  const maxSampleSize = Math.max(1, normalizeNumber(input && input.maxSampleSize, 80))
  const sampleRows = rows.slice(0, maxSampleSize)
  const containerWidth = Math.max(0, normalizeNumber(input && input.containerWidth, 0))

  const prepared = columns.map((col, idx) => {
    const key = String((col && col.key) || `col_${idx}`)
    const title = String((col && col.title) || '')
    const min = Math.max(48, normalizeNumber(col && col.min, 96))
    const max = Math.max(min, normalizeNumber(col && col.max, Math.max(min, 360)))
    const fixed = Boolean(col && col.fixed)
    const weight = Math.max(0.1, normalizeNumber(col && col.weight, 1))
    const extractor = typeof (col && col.extractor) === 'function' ? col.extractor : (row) => row && row[key]

    const titleWidth = estimateWidthByText(title)
    const sampleWidths = sampleRows.map((row) => {
      const raw = extractor(row)
      if (Array.isArray(raw)) {
        return estimateWidthByText(raw.join(' / '))
      }
      return estimateWidthByText(raw)
    })
    const p80 = percentile(sampleWidths, 0.8)
    const desired = Math.max(min, Math.min(max, Math.max(titleWidth, p80)))

    return { key, min, max, fixed, weight, desired, width: desired }
  })

  if (!prepared.length) return {}

  const totalDesired = prepared.reduce((sum, col) => sum + col.width, 0)
  const totalMin = prepared.reduce((sum, col) => sum + col.min, 0)
  const target = containerWidth > 0 ? Math.max(totalMin, containerWidth) : totalDesired

  if (totalDesired > target) {
    let overflow = totalDesired - target
    const flexCols = prepared.filter((col) => !col.fixed)
    let guard = 0
    while (overflow > 0.5 && flexCols.length && guard < 6) {
      guard += 1
      const capacities = flexCols.map((col) => Math.max(0, col.width - col.min))
      const capSum = capacities.reduce((sum, cap) => sum + cap, 0)
      if (capSum <= 0) break
      flexCols.forEach((col, index) => {
        const cap = capacities[index]
        if (!(cap > 0)) return
        const cut = Math.min(cap, (overflow * cap) / capSum)
        col.width -= cut
      })
      overflow = prepared.reduce((sum, col) => sum + col.width, 0) - target
    }
  } else if (totalDesired < target) {
    let gap = target - totalDesired
    const flexCols = prepared.filter((col) => !col.fixed)
    let guard = 0
    while (gap > 0.5 && flexCols.length && guard < 6) {
      guard += 1
      const headrooms = flexCols.map((col) => Math.max(0, col.max - col.width))
      const weighted = headrooms.map((room, index) => room * flexCols[index].weight)
      const roomSum = weighted.reduce((sum, x) => sum + x, 0)
      if (roomSum <= 0) break
      flexCols.forEach((col, index) => {
        const room = headrooms[index]
        if (!(room > 0)) return
        const inc = Math.min(room, (gap * weighted[index]) / roomSum)
        col.width += inc
      })
      gap = target - prepared.reduce((sum, col) => sum + col.width, 0)
    }
  }

  const out = {}
  prepared.forEach((col) => {
    out[col.key] = Math.round(Math.max(col.min, Math.min(col.max, col.width)))
  })
  return out
}
