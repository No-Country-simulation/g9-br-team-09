import type { InputHTMLAttributes, ReactNode } from 'react'

import { Divider } from '@/shared/components/Divider'

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  suffix?: string
  endAdornment?: ReactNode
}
export function Input({ suffix, endAdornment, ...rest }: InputProps) {
  const hasError =
    rest['aria-invalid'] === true || rest['aria-invalid'] === 'true'

  return (
    <div
      className={[
        'bg-input border-border flex items-center rounded-2xl border p-4 transition-colors hover:border-muted-foreground/60 focus-within:border-primary/60 focus-within:ring-2 focus-within:ring-primary/20',
        hasError &&
          'border-inefficient-badge-border hover:border-inefficient-badge-border focus-within:border-inefficient-badge-border focus-within:ring-inefficient-badge-border/20',
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <input
        className="text-foreground placeholder:text-muted-foreground w-full rounded-sm bg-transparent text-sm outline-none"
        {...rest}
      />
      {suffix && (
        <>
          <Divider orientation="vertical" />
          <span className="text-muted-foreground ml-3 text-sm font-medium">
            {suffix}
          </span>
        </>
      )}
      {endAdornment}
    </div>
  )
}
