import { CircleX, SearchX } from 'lucide-react'
import { useEffect } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'

import { EmptyState } from '@/shared/components/EmptyState'
import { PageHero } from '@/shared/components/PageHero'

import { HistoryCard } from '../components/HistoryCard'
import { HistoryCardsSkeleton } from '../components/HistoryCardsSkeleton'
import { Pagination } from '../components/Pagination'
import { ANALYSIS_HISTORY_PAGE_SIZE, ANALYSIS_HISTORY_PATH } from '../constants'
import { useAnalysisHistory } from '../hooks/useAnalysisHistory'

export function AnalysisHistoryPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const rawPage = Number(searchParams.get('page'))
  const page = Number.isInteger(rawPage) && rawPage >= 0 ? rawPage : 0
  const { data, isLoading, error, refetch } = useAnalysisHistory({
    page,
    size: ANALYSIS_HISTORY_PAGE_SIZE,
  })
  const location = useLocation()
  const navigate = useNavigate()

  const currentData = data?.pagina_atual === page ? data : null
  const showLoading =
    isLoading || (!error && data !== null && currentData === null)
  const emptySlotsCount = Math.max(
    ANALYSIS_HISTORY_PAGE_SIZE - (currentData?.analises.length ?? 0),
    0,
  )

  const handlePageChange = (newPage: number) => {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev)
      next.set('page', String(newPage))
      return next
    })
  }

  useEffect(() => {
    if (
      isLoading ||
      error ||
      !data ||
      data.pagina_atual !== page ||
      data.total_paginas === 0
    ) {
      return
    }

    const lastValidPage = data.total_paginas - 1
    if (page > lastValidPage) {
      setSearchParams(
        (prev) => {
          const next = new URLSearchParams(prev)
          next.set('page', String(lastValidPage))
          return next
        },
        { replace: true },
      )
    }
  }, [data, error, isLoading, page, setSearchParams])

  const handleViewDetails = (id: number) => {
    navigate(`/detalhes/${id}`, {
      state: {
        from: `${ANALYSIS_HISTORY_PATH}${location.search}`,
      },
    })
  }

  const totalElements = currentData?.total_elementos
  return (
    <main className="mx-auto max-w-6xl px-4 py-10 sm:py-14">
      <PageHero
        title="Histórico de análises"
        subtitle="Acompanhe o histórico das análises realizadas"
      />

      {showLoading && <HistoryCardsSkeleton />}

      {!showLoading && error && (
        <EmptyState
          icon={CircleX}
          title={error}
          action={{ label: 'Tentar novamente', onClick: refetch }}
        />
      )}
      {!showLoading && !error && currentData && totalElements === 0 && (
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
      {!showLoading && !error && (totalElements ?? 0) > 0 && currentData && (
        <>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {currentData.analises.map((analysis) => (
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
                className="sm:h-69.75 hidden sm:invisible sm:block"
              />
            ))}
          </div>

          <Pagination
            currentPage={currentData.pagina_atual}
            totalPages={currentData.total_paginas}
            onPageChange={handlePageChange}
          />
        </>
      )}
    </main>
  )
}
