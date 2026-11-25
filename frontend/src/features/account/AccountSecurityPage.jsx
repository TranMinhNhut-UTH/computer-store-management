import { useRef, useState } from 'react'
import { Button } from '../../components/ui/Button.jsx'
import { Card } from '../../components/ui/Card.jsx'
import { PageHeader } from '../../components/ui/PageHeader.jsx'
import { TextInput } from '../../components/ui/TextInput.jsx'

const empty = { currentPassword: '', newPassword: '', confirmation: '' }
const fields = [
  ['currentPassword', 'Mật khẩu hiện tại', 'current-password'],
  ['newPassword', 'Mật khẩu mới', 'new-password'],
  ['confirmation', 'Xác nhận mật khẩu mới', 'new-password'],
]
// Match Java Character.isWhitespace without stripping valid non-breaking spaces.
const blank = /^[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]*$/u
const fitsBcrypt = value => !blank.test(value) && value.isWellFormed() && new TextEncoder().encode(value).length <= 72

export function AccountSecurityPage({ onChangePassword, disabled }) {
  const [values, setValues] = useState(empty)
  const [errors, setErrors] = useState({})
  const [failure, setFailure] = useState('')
  const [saving, setSaving] = useState(false)
  const pending = useRef(false)

  async function submit(event) {
    event.preventDefault()
    if (disabled || pending.current) return
    const form = event.currentTarget
    const validation = {}
    for (const key of ['currentPassword', 'newPassword']) {
      if (!fitsBcrypt(values[key])) validation[key] = 'Nhập mật khẩu hợp lệ, tối đa 72 byte UTF-8.'
    }
    if (values.confirmation !== values.newPassword) validation.confirmation = 'Mật khẩu xác nhận không khớp.'
    setErrors(validation)
    setFailure('')
    const first = Object.keys(validation)[0]
    if (first) {
      form.elements.namedItem(first)?.focus()
      return
    }
    pending.current = true
    setSaving(true)
    setValues(empty)
    try {
      await onChangePassword({ currentPassword: values.currentPassword, newPassword: values.newPassword })
    } catch (error) {
      if (error.status === 400) setFailure('Không thể đổi mật khẩu. Kiểm tra mật khẩu hiện tại và nhập lại.')
      else if (error.status === 403) setFailure('Phiên bảo mật đã thay đổi. Vui lòng nhập lại và thử lại.')
      else if (error.status !== 401) setFailure('Chưa xác nhận được kết quả. Nếu phiên đã kết thúc, hãy đăng nhập bằng mật khẩu mới.')
    } finally {
      pending.current = false
      setSaving(false)
    }
  }

  return <>
    <PageHeader title="Bảo mật tài khoản" />
    <Card aria-label="Đổi mật khẩu">
      <form onSubmit={submit} noValidate>
        <fieldset disabled={saving || disabled}>
          <p>Đổi mật khẩu sẽ kết thúc tất cả phiên đăng nhập của bạn.</p>
          {failure && <p className="message message-error" role="alert">{failure}</p>}
          {fields.map(([name, label, autoComplete]) => <TextInput key={name} type="password" name={name}
            label={label} autoComplete={autoComplete} required value={values[name]} error={errors[name]}
            onChange={event => {
              const value = event.target.value
              setValues(previous => ({ ...previous, [name]: value }))
            }} />)}
          <Button type="submit" loading={saving}>Đổi mật khẩu</Button>
        </fieldset>
      </form>
    </Card>
  </>
}
