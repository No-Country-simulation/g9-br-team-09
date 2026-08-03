import { CircleX, SearchX } from 'lucide-react'
import { useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'

import { Button } from '@/shared/components/Button'
import { EmptyState } from '@/shared/components/EmptyState'
import { PageHero } from '@/shared/components/PageHero'

import { HistoryCard } from '../components/HistoryCard'
import { HistoryCardsSkeleton } from '../components/HistoryCardsSkeleton'
import { Pagination } from '../components/Pagination'
import { useAnalysisHistory } from '../hooks/useAnalysisHistory'

export const PAGE_SIZE = 6
export function AnalysisHistoryPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const rawPage = Number(searchParams.get('page'))
  const page = Number.isInteger(rawPage) && rawPage >= 0 ? rawPage : 0
  const { data, isLoading, error, refetch } = useAnalysisHistory({
    page,
    size: PAGE_SIZE,
  })
  const navigate = useNavigate()

  const emptySlotsCount = Math.max(PAGE_SIZE - (data?.analises.length ?? 0), 0)

  const handlePageChange = (newPage: number) => {
    setSearchParams((prev) => {
      prev.set('page', String(newPage))
      return prev
    })
  }

  useEffect(() => {
    if (!data || data.total_paginas === 0) return

    const lastValidPage = data.total_paginas - 1
    if (page > lastValidPage) {
      setSearchParams(
        (prev) => {
          prev.set('page', String(lastValidPage))
          return prev
        },
        { replace: true },
      )
    }
  }, [data, page, setSearchParams])

  const handleViewDetails = (id: number) => {
    navigate(`/detalhes/${id}`)
  }

  const totalElements = data?.total_elementos
  return (
    <main className="mx-auto max-w-6xl px-4 py-10 sm:py-14">
      <PageHero
        title="Histórico de análises"
        subtitle="Acompanhe o histórico das análises realizadas"
      />

      {isLoading && <HistoryCardsSkeleton />}

      {!isLoading && !data && (
        <EmptyState
          icon={CircleX}
          title={error ?? 'Nenhuma análise disponível.'}
          action={{ label: 'Tentar novamente', onClick: refetch }}
        />
      )}
      {!isLoading && data && totalElements === 0 && (
        <EmptyState
          icon={SearchX}
          title="Nenhuma análise realizada ainda"
          description="Comece sua primeira análise energética para acompanhar o histórico aqui"
          action={{
            label: 'Começar análise',
            onClick: () => navigate('/analise-energetica'),
          }}
        />
      )}
      {!isLoading && (totalElements ?? 0) > 0 && data && (
        <>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {data.analises.map((analysis) => (
              <HistoryCard
                key={analysis.id}
                analysis={analysis}
                onViewDetails={handleViewDetails}
              />
            ))}

            {Array.from({ length: emptySlotsCount }).map((_, index) => (
              <div
                key={`placeholder-${index}`}
                aria-hidden="true"
                className=" display-none sm:h-69.75 sm:invisible"
              />
            ))}
          </div>

          {error && (
            <div
              role="alert"
              className="text-destructive mt-4 flex flex-col items-center gap-2 text-sm"
            >
              <p className="text-red-500">{error}</p>
              <Button variant="ghost" onClick={refetch}>
                Tentar novamente
              </Button>
            </div>
          )}

          <Pagination
            currentPage={data.pagina_atual}
            totalPages={data.total_paginas}
            onPageChange={handlePageChange}
          />
        </>
      )}
    </main>
  )
}
