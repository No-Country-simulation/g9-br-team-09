export const CARD_BASE_CLASSES = 'bg-card sm:shadow-card rounded-2xl p-6 border'

export type Variant = 'default' | 'efficient' | 'moderate' | 'inefficient'

const VARIANT_STYLES: Record<
  Variant,
  { border: string; text: string; bg?: string }
> = {
  default: {
    border: 'border-border',
    text: 'text-foreground',
  },
  efficient: {
    border: 'border-efficient-badge-border',
    text: 'text-efficient-badge-text',
    bg: 'bg-efficient-badge-bg',
  },
  moderate: {
    border: 'border-moderate-badge-border',
    text: 'text-moderate-badge-text',
    bg: 'bg-moderate-badge-bg',
  },
  inefficient: {
    border: 'border-inefficient-badge-border',
    text: 'text-inefficient-badge-text',
    bg: 'bg-inefficient-badge-bg',
  },
}

export function getVariantStyles(variant: Variant) {
  return VARIANT_STYLES[variant]
}
