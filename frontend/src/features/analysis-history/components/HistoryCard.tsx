import { ExternalLink } from 'lucide-react'

import { Button } from '@/shared/components/Button'
import { Divider } from '@/shared/components/Divider'
import {
  categoryLabels,
  categoryVariants,
} from '@/shared/utils/analysisDisplay'
import { CARD_BASE_CLASSES } from '@/shared/utils/cardStyles'
import {
  formatAnalysisTitle,
  formatCurrencyBRL,
  formatDate,
  formatPercent,
} from '@/shared/utils/format'

import type { HistoryItem } from '../schemas/historyItem'
import { Badge } from './Badge'

interface HistoryCardProps {
  analysis: HistoryItem
  onViewDetails: (id: number) => void
}
export function HistoryCard({ analysis, onViewDetails }: HistoryCardProps) {
  return (
    <div className={`${CARD_BASE_CLASSES} border-border`}>
      <div className="flex items-center justify-between">
        <Badge variant={categoryVariants[analysis.categoria]}>
          {categoryLabels[analysis.categoria]}
        </Badge>
        <span className="text-muted-foreground text-sm">
          {formatDate(analysis.criado_em)}
        </span>
      </div>
      <h2 className="mt-3 font-semibold">{formatAnalysisTitle(analysis.id)}</h2>
      <p className="text-muted-foreground mt-3 text-sm font-semibold uppercase">
        Custo estimado
      </p>
      <p className="text-lg font-bold">
        {formatCurrencyBRL(analysis.custo_estimado_mensal)}
      </p>
      <div className="mb-2 mt-3 flex gap-2">
        <Badge>
          Probabilidade: {formatPercent(analysis.probabilidade * 100)}
        </Badge>
        <Badge>Score: {analysis.score}/100</Badge>
      </div>
      <div className="flex flex-col items-center">
        <Divider orientation="horizontal" spacing={8} />
        <Button
          variant="ghost"
          icon={ExternalLink}
          onClick={() => onViewDetails(analysis.id)}
          aria-label={`Ver detalhes da análise ${analysis.id}`}
        >
          Ver detalhes
        </Button>
      </div>
    </div>
  )
}
