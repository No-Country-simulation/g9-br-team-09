import Skeleton, { SkeletonTheme } from 'react-loading-skeleton'

import { CardSkeleton } from '@/shared/components/CardSkeleton'
import { Divider } from '@/shared/components/Divider'

export function AnalysisDetailsSkeleton() {
  return (
    <SkeletonTheme
      baseColor="var(--skeleton-base-color)"
      highlightColor="var(--skeleton-highlight-color)"
    >
      <div aria-hidden="true">
        {/* Topo */}
        <Skeleton width={220} height={36} />
        <div className="mb-8 mt-1">
          <Skeleton width={180} height={20} />
        </div>

        {/* Cards */}
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start">
          <div className="flex-1">
            <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
              {/* Coluna esquerda */}
              {Array.from({ length: 3 }).map((_, index) => (
                <CardSkeleton
                  key={index}
                  className={`lg:col-start-1 lg:row-start-${index + 1}`}
                >
                  <Skeleton width={120} height={14} />
                  <div className="mt-2">
                    <Skeleton width={170} height={24} />
                  </div>
                  <div className="mt-1">
                    <Skeleton width={320} height={16} />
                  </div>
                </CardSkeleton>
              ))}

              {/* Coluna meio */}
              <CardSkeleton className={`lg:col-start-2 lg:row-start-1`}>
                <Skeleton width={120} height={14} />
                <div className="mt-2">
                  <Skeleton width={170} height={24} />
                </div>
                <div className="mt-1">
                  <Skeleton width={320} height={16} />
                </div>
              </CardSkeleton>

              {/* Recomendações */}
              <CardSkeleton className="lg:col-start-2 lg:row-span-2 lg:row-start-2">
                <Skeleton width={140} height={16} />
                <div className="mt-6 space-y-5">
                  {Array.from({ length: 3 }).map((_, index) => (
                    <div key={index} className="flex gap-3">
                      <Skeleton circle width={18} height={18} />
                      <div className="flex-1">
                        <Skeleton height={16} count={2} />
                      </div>
                    </div>
                  ))}
                </div>
              </CardSkeleton>
            </div>
          </div>

          {/* Coluna direita */}
          <div className="lg:w-85 h-full lg:shrink-0">
            <CardSkeleton>
              <Skeleton width={200} height={20} />
              {Array.from({ length: 5 }).map((_, index) => (
                <div key={index}>
                  <Skeleton width={110} height={12} />
                  <Skeleton width={120} height={30} />
                  {index < 4 && (
                    <Divider orientation="horizontal" spacing={10} />
                  )}
                </div>
              ))}
            </CardSkeleton>
          </div>
        </div>
      </div>
      <span className="sr-only" role="status">
        Carregando detalhes da análise
      </span>
    </SkeletonTheme>
  )
}
