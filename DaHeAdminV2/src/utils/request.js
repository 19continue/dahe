import axios from 'axios'
import { clearSession, getToken } from './auth'

const DEFAULT_BASE_URL = normalize(import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:3100/api/v2')

function normalize(url) {
  const text = String(url || '').trim().replace(/\/+$/, '')
  if (!text) return ''
  if (text.endsWith('/api/v2')) return text
  if (text.endsWith('/api')) return `${text}/v2`
  return `${text}/api/v2`
}

export function getBaseUrl() {
  return DEFAULT_BASE_URL
}

const client = axios.create({
  timeout: 15000,
  baseURL: DEFAULT_BASE_URL
})

function cleanQueryParams(input) {
  if (!input || typeof input !== 'object' || Array.isArray(input)) return input
  const out = {}
  Object.keys(input).forEach((key) => {
    const value = input[key]
    if (value === undefined || value === null) return
    if (typeof value === 'string') {
      const text = value.trim()
      const lower = text.toLowerCase()
      if (!text || lower === 'undefined' || lower === 'null') return
      out[key] = text
      return
    }
    out[key] = value
  })
  return out
}

client.interceptors.request.use((config) => {
  const token = getToken()
  config.baseURL = DEFAULT_BASE_URL
  config.headers = config.headers || {}
  config.params = cleanQueryParams(config.params)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

client.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && Number(body.code) === 10200) {
      return body.data
    }
    if (body && Number(body.code) === 10100) {
      const msg = String(body.message || '')
      const lower = msg.toLowerCase()
      const shouldLogout =
        lower.includes('login') ||
        lower.includes('session') ||
        lower.includes('token') ||
        lower.includes('expired') ||
        lower.includes('approved')
      if (shouldLogout) {
        clearSession()
        if (location.pathname !== '/login') {
          location.href = '/login'
        }
      }
      return Promise.reject(new Error(msg || 'unauthorized'))
    }
    return Promise.reject(new Error((body && body.message) || 'request failed'))
  },
  (error) => Promise.reject(error)
)

export default client
