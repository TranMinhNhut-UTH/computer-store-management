const skuPattern = /^[A-Za-z0-9._-]{1,64}$/
const unsigned = /^(0|[1-9][0-9]{0,18})$/
const signedLongMax = '9223372036854775807'
export function productValues(values) {
  const optional = value => value.trim() || null
  return {
    sku: values.sku.trim(), name: values.name.trim(), categoryId: values.categoryId,
    brandName: optional(values.brandName), modelCode: optional(values.modelCode),
    description: optional(values.description), minStockLevel: values.minStockLevel,
    sellingPrice: values.sellingPrice,
  }
}

export function validateProduct(values, action) {
  const errors = {}
  if (action === 'create' || action === 'price') {
    const price = values.sellingPrice
    if (typeof price !== 'string' || !unsigned.test(price)) {
      errors.sellingPrice = 'Nhập giá nguyên không âm, tối đa 19 chữ số.'
    }
  }
  if (action === 'create' || action === 'edit') {
    if (!skuPattern.test(values.sku)) errors.sku = 'Nhập SKU hợp lệ, tối đa 64 ký tự.'
    if (!values.name || [...values.name].length > 200) errors.name = 'Nhập tên, tối đa 200 ký tự.'
    if (!/^[1-9][0-9]{0,18}$/.test(values.categoryId)
        || (values.categoryId.length === 19 && values.categoryId > signedLongMax)) errors.categoryId = 'Chọn danh mục đang sử dụng.'
    if (!unsigned.test(values.minStockLevel) || (values.minStockLevel.length === 19 && values.minStockLevel > signedLongMax)) {
      errors.minStockLevel = 'Nhập ngưỡng tồn là số nguyên không âm hợp lệ.'
    }
    for (const field of ['brandName', 'modelCode']) {
      if (values[field] && [...values[field]].length > 100) errors[field] = 'Tối đa 100 ký tự.'
    }
    if (values.description && new TextEncoder().encode(values.description).length > 65535) {
      errors.description = 'Mô tả vượt giới hạn lưu trữ.'
    }
  }
  return errors
}
