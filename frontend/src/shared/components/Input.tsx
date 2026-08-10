import type { InputHTMLAttributes, ReactNode } from 'react'

import { Divider } from '@/shared/components/Divider'

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  suffix?: string
  endAdornment?: ReactNode
}
export function Input({ suffix, endAdornment, ...rest }: InputProps) {
  return (
    <div className="bg-input border-border flex items-center rounded-2xl border p-4">
      <input
        className="text-foreground placeholder:text-muted-foreground w-full rounded-sm bg-transparent text-sm outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2"
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
