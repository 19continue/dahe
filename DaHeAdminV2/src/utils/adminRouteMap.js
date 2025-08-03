export const ASSET_CENTER_ROUTE = '/asset-center'
export const ASSET_LIBRARY_ROUTE = '/asset-center/library'
export const ASSET_REVIEW_ROUTE = '/asset-center/review'
export const ASSET_RECYCLE_ROUTE = '/asset-center/recycle'
export const ASSET_FOLDERS_ROUTE = '/asset-center/folders'

export const LEGACY_ADMIN_ROUTE_REDIRECTS = Object.freeze({
  '/assets': ASSET_CENTER_ROUTE,
  '/assets/library': ASSET_LIBRARY_ROUTE,
  '/assets/review': ASSET_REVIEW_ROUTE,
  '/assets/recycle': ASSET_RECYCLE_ROUTE,
  '/assets/folders': ASSET_FOLDERS_ROUTE
})

const ADMIN_PERMISSION_ROUTE_MAP = Object.freeze({
  [ASSET_CENTER_ROUTE]: '/assets',
  [ASSET_LIBRARY_ROUTE]: '/assets',
  [ASSET_REVIEW_ROUTE]: '/assets/review',
  [ASSET_RECYCLE_ROUTE]: '/assets',
  [ASSET_FOLDERS_ROUTE]: '/assets',
  '/miniapp-static-assets': '/assets'
})

export function normalizeAdminRoutePath(input) {
  const path = String(input || '').trim()
  if (!path) return ''
  return LEGACY_ADMIN_ROUTE_REDIRECTS[path] || path
}

export function resolveAdminPermissionPath(input) {
  const path = normalizeAdminRoutePath(input)
  if (!path) return ''
  return ADMIN_PERMISSION_ROUTE_MAP[path] || path
}
