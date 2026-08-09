import type { ReactNode } from 'react'

interface FormFieldProps {
  label: string
  htmlFor: string
  error?: string
  children: ReactNode
}

export function FormField({ label, htmlFor, error, children }: FormFieldProps) {
  return (
    <div className="flex flex-col gap-2">
      <label
        htmlFor={htmlFor}
        className="text-muted-foreground text-xs font-semibold uppercase"
      >
        {label}
      </label>
      {children}
      {error && (
        <span
          id={`${htmlFor}-error`}
          role="alert"
          className="text-xs text-red-500"
        >
          {error}
        </span>
      )}
    </div>
  )
}
