import type { LucideIcon } from 'lucide-react'
import type { ButtonHTMLAttributes } from 'react'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant: 'primary' | 'secondary' | 'ghost'
  icon?: LucideIcon
}
const baseClasses =
  'flex cursor-pointer items-center justify-center gap-2 px-2 py-3 text-sm font-medium transition-opacity hover:opacity-80 disabled:cursor-not-allowed disabled:opacity-80 sm:px-4'
const variantClasses = {
  primary: 'bg-primary text-primary-foreground font-semibold rounded-xl',
  secondary: 'bg-secondary-button border border-border rounded-3xl',
  ghost: 'rounded-lg text-foreground',
}
export function Button({
  variant,
  icon: Icon,
  className,
  children,
  type = 'button',
  ...props
}: ButtonProps) {
  return (
    <button
      type={type}
      className={[baseClasses, variantClasses[variant], className]
        .filter(Boolean)
        .join(' ')}
      {...props}
    >
      {Icon ? <Icon size={20} aria-hidden="true" /> : null}
      {children}
    </button>
  )
}
