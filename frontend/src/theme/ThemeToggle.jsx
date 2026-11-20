import { useTheme } from './useTheme.js'

export function ThemeToggle() {
  const { theme, toggleTheme } = useTheme()
  return (
    <button className="theme-toggle" type="button" aria-label="Chế độ tối" aria-pressed={theme === 'dark'} onClick={toggleTheme}>
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true">
        {theme === 'dark' ? <><circle cx="12" cy="12" r="4" /><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5" /></> : <path d="M20 14a8 8 0 0 1-10-10 8 8 0 1 0 10 10Z" />}
      </svg>
      <span>{theme === 'dark' ? 'Giao diện tối' : 'Giao diện sáng'}</span>
    </button>
  )
}
