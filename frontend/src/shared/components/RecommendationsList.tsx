import { Check } from 'lucide-react'

type RecommendationsListProps = {
  items: string[]
}

export function RecommendationsList({ items }: RecommendationsListProps) {
  return (
    <ul className="space-y-4 py-2">
      {items.map((item) => (
        <li key={item} className="flex gap-3">
          <Check
            aria-hidden="true"
            className="text-primary mt-0.5 h-5 w-5 shrink-0"
          />
          <span className="text-foreground wrap-break-word text-base font-semibold leading-6">
            {item}
          </span>
        </li>
      ))}
    </ul>
  )
}
