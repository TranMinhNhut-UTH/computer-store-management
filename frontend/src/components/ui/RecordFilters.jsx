import { Button } from './Button.jsx'
import { TextInput } from './TextInput.jsx'

export function RecordFilters({ label, statusId, searchLabel, searchName, maxLength, search, active, changed,
  onSearch, onApply, onReset, activeLabel = 'Đang sử dụng', inactiveLabel = 'Ngừng sử dụng' }) {
  return <form className="category-filters" aria-label={label} onSubmit={event => {
    event.preventDefault()
    onApply(search)
  }}>
    <TextInput label={searchLabel} name={searchName} value={search} maxLength={maxLength}
      onChange={event => onSearch(event.target.value)}
      onKeyDown={event => {
        if (event.key !== 'Enter' || event.nativeEvent.isComposing) return
        event.preventDefault()
        onApply(event.currentTarget.value)
      }} />
    <div className="csm-field">
      <label htmlFor={statusId}>Trạng thái</label>
      <select id={statusId} value={active} onChange={event => onApply(search, event.target.value)}>
        <option value="">Tất cả</option><option value="true">{activeLabel}</option><option value="false">{inactiveLabel}</option>
      </select>
    </div>
    <Button variant="secondary" disabled={!changed} onClick={onReset}>Đặt lại</Button>
  </form>
}
