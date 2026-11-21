import { useCallback, useEffect, useMemo, useState } from 'react'
import { ThemeContext } from './ThemeContext.js'
import { applyTheme, nextTheme, normalizeTheme, readSavedTheme, saveTheme } from './theme.js'

export function ThemeProvider({ children }) {
  const [theme, setThemeState] = useState(() => readSavedTheme())
  const setTheme = useCallback(value => setThemeState(normalizeTheme(value)), [])
  const toggleTheme = useCallback(() => setThemeState(current => nextTheme(current)), [])

  useEffect(() => {
    applyTheme(theme)
    saveTheme(theme)
  }, [theme])

  const value = useMemo(() => ({ theme, setTheme, toggleTheme }), [theme, setTheme, toggleTheme])
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}
