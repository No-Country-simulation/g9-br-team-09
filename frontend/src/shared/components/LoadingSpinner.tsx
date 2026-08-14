import type { HTMLAttributes } from 'react'

interface LoadingSpinnerProps extends HTMLAttributes<HTMLDivElement> {
  label?: string
  size?: 'sm' | 'md' | 'lg'
  showLabel?: boolean
  tone?: 'default' | 'inverse'
}

const sizeClasses = {
  sm: 'h-4 w-4 border-2',
  md: 'h-6 w-6 border-2',
  lg: 'h-8 w-8 border-3',
}

export function LoadingSpinner({
  label = 'Carregando...',
  size = 'md',
  showLabel = true,
  tone = 'default',
  className,
  ...props
}: LoadingSpinnerProps) {
  return (
    <div
      role="status"
      aria-live="polite"
      className={['flex items-center justify-center gap-2', className]
        .filter(Boolean)
        .join(' ')}
      {...props}
    >
      <span
        aria-hidden="true"
        className={`${sizeClasses[size]} ${
          tone === 'inverse'
            ? 'border-primary-foreground/30 border-t-primary-foreground'
            : 'border-primary/30 border-t-primary'
        } animate-spin rounded-full motion-reduce:animate-none`}
      />
      <span className={showLabel ? 'text-muted-foreground text-sm' : 'sr-only'}>
        {label}
      </span>
    </div>
  )
}
