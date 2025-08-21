const STORAGE_KEY = 'dahe.v2.dataRefreshMarks'

function normalizeTopic(topic) {
  return String(topic || '').trim()
}

function readMarks() {
  try {
    const raw = uni.getStorageSync(STORAGE_KEY)
    if (!raw || typeof raw !== 'object') return {}
    return raw
  } catch (error) {
    return {}
  }
}

function writeMarks(marks) {
  try {
    uni.setStorageSync(STORAGE_KEY, marks)
  } catch (error) {
    // ignore
  }
}

export const refreshTopics = {
  fields: () => 'fields',
  farmRecords: () => 'farm-records',
  seedBatches: () => 'seed-batches',
  fieldDetail: (fieldId) => `field-detail:${normalizeTopic(fieldId)}`,
  farmRecordDetail: (recordId) => `farm-record-detail:${normalizeTopic(recordId)}`,
  seedBatchDetail: (batchId) => `seed-batch-detail:${normalizeTopic(batchId)}`
}

export function readRefreshMark(topic) {
  const safeTopic = normalizeTopic(topic)
  if (!safeTopic) return ''
  const marks = readMarks()
  const value = marks[safeTopic]
  return String(value == null ? '' : value)
}

export function markDataChanged(topics) {
  const list = Array.isArray(topics) ? topics : [topics]
  const marks = readMarks()
  const stamp = `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  list.forEach((topic) => {
    const safeTopic = normalizeTopic(topic)
    if (!safeTopic) return
    marks[safeTopic] = stamp
  })
  writeMarks(marks)
}

export function hasDataChanged(topic, previousMark) {
  const nextMark = readRefreshMark(topic)
  return !!nextMark && nextMark !== String(previousMark || '')
}
