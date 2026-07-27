import type { LucideIcon } from 'lucide-react'
import { useId } from 'react'

import {
  CARD_BASE_CLASSES,
  getVariantStyles,
  type Variant,
} from '../utils/cardStyles'

interface CardProps {
  icon: LucideIcon
  label: string
  children: React.ReactNode
  subtitle?: string
  variant?: Variant
  score?: number
}
export function Card({
  icon: Icon,
  label,
  children,
  subtitle,
  variant = 'default',
  score,
}: CardProps) {
  const labelId = useId()
  const styles = getVariantStyles(variant)
  return (
    <section
      aria-labelledby={labelId}
      className={`${CARD_BASE_CLASSES} ${styles.border} flex h-full flex-col`}
    >
      <div className="flex items-center justify-between">
        <div id={labelId} className="text-primary mb-3 flex items-center gap-2">
          <Icon size={16} aria-hidden="true" />
          <span className="text-xs font-semibold uppercase tracking-widest">
            {label}
          </span>
        </div>
        {score !== undefined && (
          <span className={`text-xs font-semibold ${styles.text}`}>
            Score: {score}/100
          </span>
        )}
      </div>
      <div
        className={`${styles.text} flex flex-1 flex-col justify-center text-2xl font-semibold sm:text-3xl`}
      >
        {children}
      </div>
      {subtitle && (
        <p className="text-muted-foreground mt-1 text-sm">{subtitle}</p>
      )}
    </section>
  )
}
