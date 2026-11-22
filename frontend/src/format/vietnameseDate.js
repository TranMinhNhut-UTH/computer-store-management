const vietnameseDateTime = new Intl.DateTimeFormat('vi-VN', {
  timeZone: 'Asia/Ho_Chi_Minh',
  year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
})

export function formatVietnameseDate(value, { withTime = false } = {}) {
  const parts = Object.fromEntries(vietnameseDateTime.formatToParts(new Date(value))
    .map(({ type, value }) => [type, value]))
  const date = `${parts.day}/${parts.month}/${parts.year.padStart(4, '0')}`
  return withTime ? `${parts.hour}:${parts.minute} ${date}` : date
}
