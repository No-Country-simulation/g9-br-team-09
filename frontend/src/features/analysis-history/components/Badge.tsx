import type { ReactNode } from 'react'

import { getVariantStyles, type Variant } from '@/shared/utils/cardStyles'

interface BadgeProps {
  variant?: Variant
  children: ReactNode
}

export function Badge({ variant = 'default', children }: BadgeProps) {
  const styles = getVariantStyles(variant)
  return (
    <span
      className={`inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-medium ${styles.border} ${styles.bg} ${styles.text}`}
    >
      {children}
    </span>
  )
}
