export const THEME_STORAGE_KEY = 'csm-theme'
export const normalizeTheme = value => value === 'dark' ? 'dark' : 'light'
export const nextTheme = value => normalizeTheme(value) === 'light' ? 'dark' : 'light'

export function readSavedTheme(storage) {
  try { return normalizeTheme((storage ?? globalThis.localStorage)?.getItem(THEME_STORAGE_KEY)) }
  catch { return 'light' }
}

export function saveTheme(theme, storage) {
  try { (storage ?? globalThis.localStorage)?.setItem(THEME_STORAGE_KEY, normalizeTheme(theme)) }
  catch { /* Storage may be unavailable; switching still works for this page. */ }
}

export function applyTheme(theme, root = globalThis.document?.documentElement) {
  const value = normalizeTheme(theme)
  if (root) {
    root.dataset.theme = value
    root.style.colorScheme = value
  }
}
