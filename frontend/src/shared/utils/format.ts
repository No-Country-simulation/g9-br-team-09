const brlFormatter = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
})

const decimalFormatter = new Intl.NumberFormat('pt-BR', {
  style: 'decimal',
  maximumFractionDigits: 2,
})

const percentFormatter = new Intl.NumberFormat('pt-BR', {
  maximumFractionDigits: 0,
})

const dateFormatter = new Intl.DateTimeFormat('pt-BR')

export function formatCurrencyBRL(value: number): string {
  return brlFormatter.format(value)
}

export function formatPercent(value: number): string {
  return `${percentFormatter.format(value)}%`
}

export function formatKWh(value: number): string {
  return `${decimalFormatter.format(value)} kWh`
}

export function formatDate(dateString: string): string {
  return dateFormatter.format(new Date(dateString))
}

export function formatAnalysisTitle(id: number): string {
  return `Análise #${String(id).padStart(2, '0')}`
}
