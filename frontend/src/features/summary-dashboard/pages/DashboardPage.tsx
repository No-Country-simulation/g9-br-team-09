import { useNavigate } from 'react-router-dom'

import { EmptyState } from '@/shared/components/EmptyState'
import { PageHero } from '@/shared/components/PageHero'

import { SummaryCards } from '../components/SummaryCards'
import { SummaryCardsSkeleton } from '../components/SummaryCardsSkeleton'
import { useAnalysisSummary } from '../hooks/useAnalysisSummary'

export function SummaryDashboardPage() {
  const { data, isLoading, error, refetch } = useAnalysisSummary()
  const navigate = useNavigate()
  const totalAnalyses = data?.total_analises

  return (
    <main className="mx-auto max-w-6xl px-4 py-10 sm:py-14">
      <PageHero
        title="Painel de análises"
        subtitle="Resumo de todas as análises realizadas"
      />

      {isLoading && <SummaryCardsSkeleton />}

      {!isLoading && (error || !data) && (
        <EmptyState
          title={error ?? 'Nenhum dado de resumo disponível.'}
          action={{ label: 'Tentar novamente', onClick: refetch }}
        />
      )}
      {!isLoading && !error && data && totalAnalyses === 0 && (
        <EmptyState
          title="Nenhuma análise realizada ainda"
          description="Comece sua primeira análise energética para acompanhar o resumo aqui"
          action={{
            label: 'Começar análise',
            onClick: () => navigate('/analise-energetica'),
          }}
        />
      )}
      {!isLoading && !error && (totalAnalyses ?? 0) > 0 && data && (
        <SummaryCards data={data} />
      )}
    </main>
  )
}
