import 'react-loading-skeleton/dist/skeleton.css'

import Skeleton, { SkeletonTheme } from 'react-loading-skeleton'

import { CardSkeleton } from '@/shared/components/CardSkeleton'

export function SummaryCardsSkeleton() {
  return (
    <SkeletonTheme
      baseColor="var(--skeleton-base-color)"
      highlightColor="var(--skeleton-highlight-color)"
    >
      <div className="space-y-6" aria-hidden="true">
        {/* Cards superiores */}
        <div className="grid gap-4 sm:max-w-4xl md:grid-cols-3">
          {Array.from({ length: 3 }).map((_, index) => (
            <CardSkeleton key={index}>
              <Skeleton width={150} height={16} />

              <div className="mt-3">
                <Skeleton width={140} height={42} />
              </div>
            </CardSkeleton>
          ))}
        </div>

        {/* Card do gráfico */}
        <CardSkeleton className="sm:max-w-4xl">
          <Skeleton width={220} height={16} />

          <div className="mt-3 flex flex-col gap-8 sm:flex-row">
            {/* Donut */}
            <div className="flex items-center justify-center">
              <Skeleton circle width={140} height={140} />
            </div>

            {/* Legenda */}
            <div className="flex w-full flex-1 flex-col justify-around gap-3">
              {Array.from({ length: 3 }).map((_, index) => (
                <div key={index} className="flex items-center gap-3">
                  <Skeleton circle width={10} height={10} />

                  <Skeleton width={100} height={18} />

                  <div className="flex-1">
                    <Skeleton height={2} />
                  </div>

                  <Skeleton width={70} height={18} />
                </div>
              ))}
            </div>
          </div>
        </CardSkeleton>
      </div>
      <span className="sr-only" role="status">
        Carregando resumo de análises
      </span>
    </SkeletonTheme>
  )
}
