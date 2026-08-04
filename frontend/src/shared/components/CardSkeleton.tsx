import type { HTMLAttributes, ReactNode } from 'react'

import { CARD_BASE_CLASSES } from '@/shared/utils/cardStyles'

interface CardSkeletonProps extends HTMLAttributes<HTMLDivElement> {
  children: ReactNode
}

export function CardSkeleton({
  className = '',
  children,
  ...props
}: CardSkeletonProps) {
  return (
    <div
      className={`${CARD_BASE_CLASSES} border-border ${className}`.trim()}
      {...props}
    >
      {children}
    </div>
  )
}
