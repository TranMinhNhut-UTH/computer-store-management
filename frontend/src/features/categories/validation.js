// Keep code normalization consistent with IdentifierNormalizer.businessCode (ASCII trim/uppercase).
export function categoryValues(input) {
  return {
    code: input.code.replace(/^[\x09-\x0d\x20]+|[\x09-\x0d\x20]+$/g, '').replace(/[a-z]/g, c => c.toUpperCase()),
    name: input.name.trim(), description: input.description.trim() || null,
  }
}
export function validateCategory(input) {
  const value = categoryValues(input)
  const errors = {}
  if (!/^[A-Z0-9._-]{1,50}$/.test(value.code)) errors.code = 'Nhập mã từ 1–50 ký tự: chữ A–Z, số, dấu chấm, gạch dưới hoặc gạch ngang.'
  if (!value.name || Array.from(value.name).length > 120) errors.name = 'Nhập tên danh mục từ 1–120 ký tự.'
  if (Array.from(value.description ?? '').length > 500) errors.description = 'Mô tả không vượt quá 500 ký tự.'
  return errors
}
