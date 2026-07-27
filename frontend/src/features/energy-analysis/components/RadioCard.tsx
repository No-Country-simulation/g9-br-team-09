import type { InputHTMLAttributes } from 'react'

interface RadioCardProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
}

export function RadioCard({ label, ...rest }: RadioCardProps) {
  return (
    <label className="bg-input has-checked:ring-primary has-checked:ring-2 focus-within:ring-primary border-border hover:border-primary flex cursor-pointer items-center gap-3 rounded-2xl border px-4 py-6 focus-within:ring-2">
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
