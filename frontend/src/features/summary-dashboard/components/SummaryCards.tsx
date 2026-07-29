import { ChartPie, Receipt, TrendingUp, Zap } from 'lucide-react'

import { Card } from '@/shared/components/Card'
import { formatCurrencyBRL, formatKWh } from '@/shared/utils/format'

import type { AnalysisSummaryResponse } from '../types/analysisSummary'
import { CategoryDonut } from './CategoryDonut'

interface SummaryCardsProps {
  data: AnalysisSummaryResponse
}

export function SummaryCards({ data }: SummaryCardsProps) {
  const summaryCards = [
    {
      id: 'total',
      icon: TrendingUp,
      label: 'Total de análises',
      value: data.total_analises,
    },
    {
      id: 'averageCost',
      icon: Receipt,
      label: 'Custo médio',
      value: formatCurrencyBRL(data.media_custo_mensal),
    },
    {
      id: 'averageConsumption',
      icon: Zap,
      label: 'Consumo médio',
      value: formatKWh(data.media_consumo_kwh),
    },
    {
      id: 'distributionByCategory',
      icon: ChartPie,
      label: 'Distribuição por categoria',
      className: 'md:col-span-3',
      value: (
        <CategoryDonut
          data={{
            EFICIENTE: data.total_eficiente,
            MODERADO: data.total_moderado,
            INEFICIENTE: data.total_ineficiente,
          }}
          total={data.total_analises}
        />
      ),
    },
  ]
  return (
    <section className="grid gap-4 sm:max-w-4xl md:grid-cols-3">
      {summaryCards.map((card) => (
        <div key={card.id} className={card.className}>
          <Card icon={card.icon} label={card.label}>
            {card.value}
          </Card>
        </div>
      ))}
    </section>
  )
}
