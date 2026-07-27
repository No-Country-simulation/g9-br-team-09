import type { LucideIcon } from 'lucide-react'

import { Button } from './Button'

type EmptyStateAction = {
  label: string
  onClick: () => void
}

type EmptyStateProps = {
  icon?: LucideIcon
  title: string
  description?: string
  action?: EmptyStateAction
}

export function EmptyState({
  icon: Icon,
  title,
  description,
  action,
}: EmptyStateProps) {
  return (
    <div className="border-border flex flex-col items-center justify-center gap-3 rounded-2xl border py-16 text-center">
      {Icon && (
        <Icon aria-hidden="true" className="text-muted-foreground h-10 w-10" />
      )}
      <p className="text-foreground text-lg font-semibold">{title}</p>
      {description && (
        <p className="text-muted-foreground max-w-sm text-sm">{description}</p>
      )}
      {action && (
        <Button variant="primary" onClick={action.onClick}>
          {action.label}
        </Button>
      )}
    </div>
  )
}
