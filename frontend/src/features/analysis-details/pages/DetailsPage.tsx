import { ArrowLeft, CircleX, SearchX } from 'lucide-react'
import { useNavigate, useParams } from 'react-router-dom'

import { Button } from '@/shared/components/Button'
import { EmptyState } from '@/shared/components/EmptyState'
import { PageHero } from '@/shared/components/PageHero'
import { formatAnalysisTitle, formatDate } from '@/shared/utils/format'

import { AnalyzedDataCard } from '../components/AnalyzedDataCard'
import { DetailsCards } from '../components/DetailsCards'
import { AnalysisDetailsSkeleton } from '../components/DetailsCardsSkeleton'
import { useAnalysisDetails } from '../hooks/useAnalysisDetails'

export function AnalysisDetailsPage() {
  const { id } = useParams<{ id: string }>()
  const analysisId = Number(id)

  const { data, isLoading, error, refetch } = useAnalysisDetails(analysisId)
  const navigate = useNavigate()

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
            onClick: () => navigate('/historico'),
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
            onClick={() => navigate(-1)}
          >
            Voltar ao histórico
          </Button>
        </>
      )}
    </main>
  )
}
