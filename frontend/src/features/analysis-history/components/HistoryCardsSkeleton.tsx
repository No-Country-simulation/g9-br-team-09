import Skeleton, { SkeletonTheme } from 'react-loading-skeleton'

import { CardSkeleton } from '@/shared/components/CardSkeleton'
import { Divider } from '@/shared/components/Divider'
import { LoadingSpinner } from '@/shared/components/LoadingSpinner'

import { ANALYSIS_HISTORY_PAGE_SIZE } from '../constants'

export function HistoryCardsSkeleton() {
  return (
    <SkeletonTheme
      baseColor="var(--skeleton-base-color)"
      highlightColor="var(--skeleton-highlight-color)"
    >
      <LoadingSpinner
        label="Carregando histórico de análises"
        size="sm"
        className="mb-4 justify-start"
      />
      <div aria-hidden="true">
        {/* Cards */}
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: ANALYSIS_HISTORY_PAGE_SIZE }).map(
            (_, index) => (
              <CardSkeleton key={index}>
                {/* topo */}
                <div className="flex items-center justify-between">
                  <Skeleton width={73} height={22} borderRadius={999} />
                  <Skeleton width={77} height={17} />
                </div>

                {/* título */}
                <div className="mt-3">
                  <Skeleton width={120} height={20} />
                </div>

                {/* label */}
                <div className="mt-3">
                  <Skeleton width={120} height={17} />
                </div>

                {/* valor */}
                <div>
                  <Skeleton width={110} height={28} />
                </div>

                {/* badges */}
                <div className="mb-2 mt-3 flex gap-2">
                  <Skeleton width={110} height={20} borderRadius={999} />
                  <Skeleton width={90} height={20} borderRadius={999} />
                </div>

                {/* botão */}
                <div className="flex flex-col items-center">
                  <Divider orientation="horizontal" spacing={8} />
                  <div className="p-2 sm:px-4">
                    <Skeleton width={110} height={17} />
                  </div>
                </div>
              </CardSkeleton>
            ),
          )}
        </div>
        <div className="mt-8 flex justify-center gap-3">
          <Skeleton width={32} height={32} borderRadius={10} />
          <Skeleton width={90} height={32} borderRadius={8} />
          <Skeleton width={32} height={32} borderRadius={10} />
        </div>
      </div>
    </SkeletonTheme>
  )
}
