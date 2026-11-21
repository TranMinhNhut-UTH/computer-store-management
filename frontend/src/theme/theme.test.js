import assert from 'node:assert/strict'
import test from 'node:test'
import fs from 'node:fs'
import vm from 'node:vm'
import { THEME_STORAGE_KEY, applyTheme, nextTheme, normalizeTheme, readSavedTheme, saveTheme } from './theme.js'

function storage(initial = null) {
  const entries = new Map(initial === null ? [] : [[THEME_STORAGE_KEY, initial]])
  return { entries, getItem: key => entries.get(key) ?? null, setItem: (key, value) => entries.set(key, value) }
}

test('light is default and invalid preferences fall back to light', () => {
  for (const value of [null, undefined, '', 'system', 'DARK', 'invalid', { theme: 'dark' }]) {
    assert.equal(normalizeTheme(value), 'light')
  }
  assert.equal(readSavedTheme(storage()), 'light')
  assert.equal(readSavedTheme(storage('invalid')), 'light')
})

test('switches both ways, applies document theme and persists only one non-sensitive key', () => {
  const saved = storage()
  const root = { dataset: {}, style: {} }
  let theme = 'light'
  for (const expected of ['dark', 'light']) {
    theme = nextTheme(theme)
    applyTheme(theme, root)
    saveTheme(theme, saved)
    assert.equal(theme, expected)
    assert.equal(root.dataset.theme, expected)
    assert.equal(root.style.colorScheme, expected)
    assert.equal(readSavedTheme(saved), expected)
    assert.deepEqual([...saved.entries], [[THEME_STORAGE_KEY, expected]])
  }
  saveTheme('untrusted-value', saved)
  assert.equal(readSavedTheme(saved), 'light')
})

test('storage read/write failures are safe and do not prevent theme application', () => {
  const denied = { getItem() { throw new Error('denied') }, setItem() { throw new Error('denied') } }
  assert.equal(readSavedTheme(denied), 'light')
  assert.doesNotThrow(() => saveTheme('dark', denied))
  const root = { dataset: {}, style: {} }
  applyTheme('dark', root)
  assert.equal(root.dataset.theme, 'dark')
})

test('early initializer and runtime agree without writing auth or reloading', () => {
  const html = fs.readFileSync(new URL('../../index.html', import.meta.url), 'utf8')
  const script = html.match(/<script>([\s\S]*?)<\/script>/)[1]
  assert.ok(html.indexOf('<script>') < html.indexOf('<body>'))
  for (const value of [null, 'light', 'dark', 'invalid']) {
    const saved = storage(value)
    const root = { dataset: {}, style: {} }
    vm.runInNewContext(script, { localStorage: saved, document: { documentElement: root } })
    assert.equal(root.dataset.theme, readSavedTheme(saved))
    assert.equal(saved.entries.size, value === null ? 0 : 1)
  }
  const root = { dataset: {}, style: {} }
  vm.runInNewContext(script, { localStorage: { getItem() { throw new Error('denied') } }, document: { documentElement: root } })
  assert.equal(root.dataset.theme, 'light')
})

function luminance(hex) {
  const channels = hex.match(/[a-f\d]{2}/gi).map(value => {
    const channel = parseInt(value, 16) / 255
    return channel <= .04045 ? channel / 12.92 : ((channel + .055) / 1.055) ** 2.4
  })
  return channels[0] * .2126 + channels[1] * .7152 + channels[2] * .0722
}
const contrast = (a, b) => (Math.max(luminance(a), luminance(b)) + .05) / (Math.min(luminance(a), luminance(b)) + .05)

test('both themes meet text 4.5:1 and input/focus 3:1 for declared token pairs', () => {
  const css = fs.readFileSync(new URL('../styles/themes.css', import.meta.url), 'utf8')
  const blocks = [...css.matchAll(/\{([^}]+)\}/g)].map(match =>
    Object.fromEntries([...match[1].matchAll(/--csm-([\w-]+):\s*(#[a-f\d]{6})/gi)].map(item => [item[1], item[2]])))
  assert.equal(blocks.length, 2)
  for (const colors of blocks) {
    for (const [foreground, background] of [['text', 'bg'], ['text', 'surface'], ['muted', 'bg'], ['muted', 'surface'], ['muted', 'input-bg'], ['text', 'primary-soft'], ['on-primary', 'primary'], ['on-primary', 'primary-hover'], ['success', 'success-soft'], ['error', 'error-soft'], ['warning', 'warning-soft'], ['disabled', 'disabled-bg']]) {
      assert.ok(contrast(colors[foreground], colors[background]) >= 4.5, `${foreground}/${background}`)
    }
    for (const [foreground, background] of [['input-border', 'input-bg'], ['focus', 'surface'], ['focus', 'bg']]) {
      assert.ok(contrast(colors[foreground], colors[background]) >= 3, `${foreground}/${background}`)
    }
  }
})

test('login styles consume tokens instead of hardcoded colors', () => {
  const css = fs.readFileSync(new URL('../index.css', import.meta.url), 'utf8')
  assert.doesNotMatch(css, /#[a-f\d]{3,8}\b|rgba?\(/i)
})
