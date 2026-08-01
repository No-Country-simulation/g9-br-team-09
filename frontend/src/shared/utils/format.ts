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

export function formatCurrencyBRL(value: number): string {
  return brlFormatter.format(value)
}

export function formatPercent(value: number): string {
  return `${percentFormatter.format(value)}%`
}

export function formatKWh(value: number): string {
  return `${decimalFormatter.format(value)} kWh`
}
