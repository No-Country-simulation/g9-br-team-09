import {
  Bot,
  type LucideIcon,
  Receipt,
  SearchX,
  Sparkles,
  Target,
  Zap,
} from 'lucide-react'
import type { ReactNode } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'

import { Card } from '@/shared/components/Card'
import { EmptyState } from '@/shared/components/EmptyState'
import { PageHero } from '@/shared/components/PageHero'
import { RecommendationsList } from '@/shared/components/RecommendationsList'
import {
  categoryLabels,
  categoryVariants,
  sourceLabels,
} from '@/shared/utils/analysisDisplay'
import type { Variant } from '@/shared/utils/cardStyles'
import { formatCurrencyBRL } from '@/shared/utils/format'

import { ProbabilityRing } from '../components/ProbabilityRing'
import type { CreateAnalysisResponse } from '../types/analysis'

type AnalysisResultsLocationState = {
  result?: CreateAnalysisResponse
}

type ResponseDataCard = {
  id: string
  icon: LucideIcon
  label: string
  subtitle?: string
  variant?: Variant
  score?: number
  className?: string
  value: ReactNode
}

export function AnalysisResultsPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const state = location.state as AnalysisResultsLocationState | null
  const result = state?.result

  if (!result) {
    return (
      <main className="mx-auto max-w-6xl px-4 py-10 sm:py-14">
        <PageHero
          title="Resultado da análise"
          subtitle="Confira a classificação do consumo energético do seu imóvel e as recomendações para melhorar sua eficiência"
        />
        <EmptyState
          icon={SearchX}
          title="Nenhum resultado para exibir"
          description="Faça uma nova análise para ver a classificação de eficiência do seu imóvel."
          action={{
            label: 'Fazer nova análise',
            onClick: () => navigate('/analise-energetica'),
          }}
        />
      </main>
    )
  }

  const responseDataCard: ResponseDataCard[] = [
    {
      id: 'category',
      icon: Zap,
      label: 'Categoria',
      value: categoryLabels[result.categoria],
      subtitle: 'Perfil energético identificado',
      variant: categoryVariants[result.categoria],
      score: result.score,
    },
    {
      id: 'cost',
      icon: Receipt,
      label: 'Custo estimado',
      value: formatCurrencyBRL(result.custo_estimado_mensal),
      subtitle: 'Estimativa baseada no consumo informado',
    },
    {
      id: 'source',
      icon: Bot,
      label: 'Método de análise',
      value: sourceLabels[result.fonte_classificacao],
      subtitle: 'Origem da classificação gerada',
    },
    {
      id: 'probability',
      icon: Target,
      label: 'Probabilidade',
      value: <ProbabilityRing value={result.probabilidade} />,
    },
    {
      id: 'recommendations',
      icon: Sparkles,
      label: 'Recomendações',
      className: 'md:col-span-2',
      value: <RecommendationsList items={result.recomendacoes} />,
    },
  ]

  return (
    <main className="mx-auto max-w-6xl px-4 py-10 sm:py-14">
      <PageHero
        title="Resultado da análise"
        subtitle="Confira a classificação do consumo energético do seu imóvel e as recomendações para melhorar sua eficiência"
      />
      <section className="grid gap-4 md:grid-cols-3">
        {responseDataCard.map((card) => (
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
    </main>
  )
}
