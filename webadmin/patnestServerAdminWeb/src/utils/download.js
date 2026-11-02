export function downloadCSV(response, baseName) {
  const stamp = new Date().toISOString().slice(0, 19).replace(/[-:T]/g, '')
  const blob = new Blob([response.data], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${baseName}-${stamp}.csv`
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
