import { Clock, Home, Hourglass, Plug, Zap } from 'lucide-react'

import { Divider } from '@/shared/components/Divider'
import { propertyTypeLabels } from '@/shared/utils/analysisDisplay'
import { CARD_BASE_CLASSES } from '@/shared/utils/cardStyles'
import { formatKWh } from '@/shared/utils/format'

import type { AnalysisDetail } from '../schemas/analysisDetails'

interface AnalyzedDataCardProps {
  data: AnalysisDetail
}

function getRows(data: AnalysisDetail) {
  return [
    {
      id: 'propertyType',
      icon: Home,
      label: 'Tipo imóvel',
      value: propertyTypeLabels[data.tipo_imovel],
    },
    {
      id: 'equipmentCount',
      icon: Plug,
      label: 'Equipamentos elétricos',
      value: data.quantidade_equipamentos,
    },
    {
      id: 'consumption',
      icon: Zap,
      label: 'Consumo mensal',
      value: formatKWh(data.consumo_kwh),
    },
    {
      id: 'peakHours',
      icon: Clock,
      label: 'Horário de pico',
      value: data.uso_horario_pico ? 'Sim' : 'Não',
    },
    {
      id: 'highUsageHours',
      icon: Hourglass,
      label: 'Horas de maior consumo',
      value: data.horas_alto_consumo,
    },
  ]
}

export function AnalyzedDataCard({ data }: AnalyzedDataCardProps) {
  const rows = getRows(data)

  return (
    <section
      aria-labelledby="analyzed-data-heading"
      className={`${CARD_BASE_CLASSES} border-border flex h-full flex-col`}
    >
      <h2
        id="analyzed-data-heading"
        className="text-primary text-base font-semibold"
      >
        Dados analisados
      </h2>

      <dl>
        {rows.map((row, index) => (
          <div key={row.id} className={index === 0 ? 'mt-3' : ''}>
            {index > 0 && <Divider orientation="horizontal" spacing={12} />}

            <dt className="text-muted-foreground flex items-center gap-2 text-xs font-semibold uppercase">
              <row.icon aria-hidden="true" className="size-4" />
              {row.label}
            </dt>
            <dd className="mt-1 text-xl font-semibold sm:text-2xl">
              {row.value}
            </dd>
          </div>
        ))}
      </dl>
    </section>
  )
}
