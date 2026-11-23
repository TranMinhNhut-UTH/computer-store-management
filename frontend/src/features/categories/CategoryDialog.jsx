import { useEffect, useRef } from 'react'
import { Button } from '../../components/ui/Button.jsx'

export function CategoryDialog({ title, busy, ready = true, onClose, children,
  titleId = 'category-dialog-title', fallbackFocusId = 'category-create' }) {
  const ref = useRef(null)
  useEffect(() => {
    const opener = document.activeElement
    const dialog = ref.current
    const priorOverflow = document.body.style.overflow
    dialog.showModal()
    document.body.style.overflow = 'hidden'
    dialog.querySelector('[data-initial-focus]')?.focus()
    return () => {
      dialog.close()
      document.body.style.overflow = priorOverflow
      if (opener?.isConnected && !opener.disabled) opener.focus()
      if (document.activeElement !== opener || opener === document.body) {
        const fallback = document.getElementById(fallbackFocusId) ?? document.getElementById('page-title')
        fallback?.focus()
      }
    }
  }, [fallbackFocusId])
  useEffect(() => {
    if (ready) ref.current.querySelector('[data-initial-focus]')?.focus()
  }, [ready])
  return <dialog ref={ref} className="category-dialog" aria-labelledby={titleId}
    onCancel={event => { event.preventDefault(); if (!busy) onClose() }}
    onClick={event => { if (event.target === event.currentTarget && !busy) onClose() }}>
    <div className="category-dialog-body">
      <header><h2 id={titleId}>{title}</h2><Button variant="ghost" disabled={busy} onClick={onClose} aria-label="Đóng hộp thoại">Đóng</Button></header>
      {children}
    </div>
  </dialog>
}
