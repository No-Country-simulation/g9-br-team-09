import { ArrowLeft, CircleX, SearchX } from 'lucide-react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'

import { ANALYSIS_HISTORY_PATH } from '@/features/analysis-history/constants'
import { Button } from '@/shared/components/Button'
import { EmptyState } from '@/shared/components/EmptyState'
import { PageHero } from '@/shared/components/PageHero'
import { formatAnalysisTitle, formatDate } from '@/shared/utils/format'

import { AnalyzedDataCard } from '../components/AnalyzedDataCard'
import { DetailsCards } from '../components/DetailsCards'
import { AnalysisDetailsSkeleton } from '../components/DetailsCardsSkeleton'
import { useAnalysisDetails } from '../hooks/useAnalysisDetails'

function parseAnalysisId(rawId: string | undefined) {
  if (!rawId || !/^[1-9]\d*$/.test(rawId)) return null

  const parsedId = Number(rawId)
  return Number.isSafeInteger(parsedId) ? parsedId : null
}

function resolveHistoryUrl(state: unknown) {
  if (typeof state !== 'object' || state === null || !('from' in state)) {
    return ANALYSIS_HISTORY_PATH
  }

  const from = state.from
  if (
    typeof from === 'string' &&
    (from === ANALYSIS_HISTORY_PATH ||
      from.startsWith(`${ANALYSIS_HISTORY_PATH}?`))
  ) {
    return from
  }

  return ANALYSIS_HISTORY_PATH
}

export function AnalysisDetailsPage() {
  const { id } = useParams<{ id: string }>()
  const analysisId = parseAnalysisId(id)

  const { data, isLoading, error, refetch } = useAnalysisDetails(analysisId)
  const location = useLocation()
  const navigate = useNavigate()
  const historyUrl = resolveHistoryUrl(location.state)

  return (
    <main className="mx-auto max-w-6xl px-4 py-10 sm:py-14">
      {isLoading && <AnalysisDetailsSkeleton />}
      {!isLoading && error && (
        <EmptyState
          icon={CircleX}
          title={error}
          action={{ label: 'Tentar novamente', onClick: refetch }}
        />
      )}

      {!isLoading && !error && data === null && (
        <EmptyState
          icon={SearchX}
          title="Análise não encontrada"
          description="A análise que você está procurando não existe ou foi removida."
          action={{
            label: 'Voltar ao histórico',
            onClick: () => navigate(historyUrl),
          }}
        />
      )}

      {!isLoading && !error && data && (
        <>
          <PageHero
            title={formatAnalysisTitle(data.id)}
            subtitle={`Data da análise: ${formatDate(data.criado_em)}`}
          />
          <div className="flex flex-col gap-4 lg:flex-row lg:items-stretch">
            <div className="flex-1">
              <DetailsCards data={data} />
            </div>
            <div className="lg:w-85 lg:shrink-0 lg:self-stretch">
              <AnalyzedDataCard data={data} />
            </div>
          </div>
          <Button
            icon={ArrowLeft}
            variant="ghost"
            className="mt-5"
            onClick={() => navigate(historyUrl)}
          >
            Voltar ao histórico
          </Button>
        </>
      )}
    </main>
  )
}
