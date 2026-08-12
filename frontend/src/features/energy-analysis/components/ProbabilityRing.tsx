import { formatPercent } from '@/shared/utils/format'

type ProbabilityRingProps = {
  value: number
}

export function ProbabilityRing({ value }: ProbabilityRingProps) {
  const percentage = value * 100

  const ringStyle = {
    background: `conic-gradient(#11a37f 0deg ${(percentage / 100) * 360}deg, #e5e7eb ${(percentage / 100) * 360}deg 360deg)`,
  }

  return (
    <div className="flex flex-col items-center justify-center">
      <div
        className="flex h-24 w-24 items-center justify-center rounded-full p-3"
        style={ringStyle}
        role="img"
        aria-label={`Probabilidade: ${formatPercent(percentage)}`}
      >
        <div className="bg-card text-foreground flex h-full w-full items-center justify-center rounded-full text-2xl font-semibold">
          {formatPercent(percentage)}
        </div>
      </div>
      <p className="text-muted-foreground mt-2 text-center text-sm font-medium">
        Nível de confiança da classificação obtida
      </p>
    </div>
  )
}
