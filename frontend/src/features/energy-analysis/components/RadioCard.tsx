import type { InputHTMLAttributes } from 'react'

interface RadioCardProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
}

export function RadioCard({ label, ...rest }: RadioCardProps) {
  const hasError =
    rest['aria-invalid'] === true || rest['aria-invalid'] === 'true'

  return (
    <label
      className={[
        'bg-input border-border hover:border-muted-foreground/60 has-checked:border-primary/60 has-checked:ring-primary/20 focus-within:border-primary/60 focus-within:ring-primary/20 flex cursor-pointer items-center gap-3 rounded-2xl border px-4 py-6 transition-colors has-checked:ring-2 focus-within:ring-2',
        hasError &&
          'border-inefficient-badge-border hover:border-inefficient-badge-border focus-within:border-inefficient-badge-border focus-within:ring-inefficient-badge-border/20',
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <span className="border-border has-checked:border-0 has-checked:bg-primary relative flex h-5 w-5 shrink-0 items-center justify-center rounded-full border-2">
        <input
          type="radio"
          className="absolute inset-0 cursor-pointer opacity-0"
          {...rest}
        />
      </span>
      <span className="text-foreground text-base font-semibold">{label}</span>
    </label>
  )
}
