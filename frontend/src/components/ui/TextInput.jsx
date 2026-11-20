import { useId } from 'react'

export function TextInput({ label, error, hint, id, ...props }) {
  const generated = useId()
  const inputId = id ?? generated
  return <div className="csm-field"><label htmlFor={inputId}>{label}</label><input {...props} id={inputId} aria-invalid={Boolean(error) || undefined} aria-describedby={error || hint ? `${inputId}-help` : undefined} />{(error || hint) && <p id={`${inputId}-help`} className={error ? 'csm-field-error' : 'csm-muted'}>{error || hint}</p>}</div>
}
