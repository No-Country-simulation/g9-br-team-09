import { Bot, Receipt, Sparkles, Target, Zap } from 'lucide-react'

import { Card } from '@/shared/components/Card'
import { RecommendationsList } from '@/shared/components/RecommendationsList'
import type { AnalysisClassification } from '@/shared/types/analysis'
import {
  categoryLabels,
  categoryVariants,
  sourceLabels,
} from '@/shared/utils/analysisDisplay'
import { formatCurrencyBRL, formatPercent } from '@/shared/utils/format'

interface DetailsCardsProps {
  data: AnalysisClassification
}
export function DetailsCards({ data }: DetailsCardsProps) {
  const cards = [
    {
      id: 'category',
      icon: Zap,
      label: 'Categoria',
      value: categoryLabels[data.categoria],
      subtitle: 'Perfil energético identificado',
      variant: categoryVariants[data.categoria],
      score: data.score,
      className: 'lg:col-start-1 lg:row-start-1',
    },
    {
      id: 'cost',
      icon: Receipt,
      label: 'Custo estimado',
      value: formatCurrencyBRL(data.custo_estimado_mensal),
      subtitle: 'Estimativa baseada no consumo informado',
      className: 'lg:col-start-2 lg:row-start-1',
    },
    {
      id: 'source',
      icon: Bot,
      label: 'Método de análise',
      value: sourceLabels[data.fonte_classificacao],
      subtitle: 'Origem da classificação gerada',
      className: 'lg:col-start-1 lg:row-start-2',
    },
    {
      id: 'probability',
      icon: Target,
      label: 'Probabilidade',
      value: formatPercent(data.probabilidade * 100),
      subtitle: 'Nível de confiança da classificação obtida',
      className: 'lg:col-start-1 lg:row-start-3',
    },
    {
      id: 'recommendations',
      icon: Sparkles,
      label: 'Recomendações',
      value: <RecommendationsList items={data.recomendacoes} />,
      className: 'lg:col-start-2 lg:row-start-2 lg:row-span-2',
    },
  ]

  return (
    <section
      aria-label="Resultado da classificação energética"
      className="grid grid-cols-1 gap-4 md:grid-cols-2"
    >
      {cards.map((card) => (
        <div key={card.id} className={card.className}>
          <Card
            icon={card.icon}
            label={card.label}
            subtitle={card.subtitle}
            variant={card.variant}
            score={card.score}
          >
            {card.value}
          </Card>
        </div>
      ))}
    </section>
  )
}
