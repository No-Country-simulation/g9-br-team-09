import { Pie, PieChart, ResponsiveContainer } from 'recharts'

import { Divider } from '@/shared/components/Divider'
import type { Category } from '@/shared/types/analysis'
import { categoryLabels } from '@/shared/utils/analysisDisplay'
import { formatPercent } from '@/shared/utils/format'

interface CategoryDonutProps {
  total: number
  data: Record<Category, number>
}
const CATEGORY_COLORS: Record<Category, string> = {
  EFICIENTE: 'var(--efficient-badge-border)',
  MODERADO: 'var(--moderate-badge-border)',
  INEFICIENTE: 'var(--inefficient-badge-border)',
}
export function CategoryDonut({ total, data }: CategoryDonutProps) {
  const chartData = (Object.keys(data) as Category[]).map((category) => ({
    category,
    name: categoryLabels[category],
    value: data[category],
    fill: CATEGORY_COLORS[category],
  }))

  return (
    <div className=" flex flex-col items-center gap-8 sm:flex-row">
      <div className="relative h-40 w-40">
        <div aria-hidden="true" className="h-full w-full">
          <ResponsiveContainer>
            <PieChart accessibilityLayer={false}>
              <Pie
                data={chartData}
                dataKey="value"
                nameKey="name"
                innerRadius={45}
                outerRadius={65}
                paddingAngle={2}
                stroke="none"
              />
            </PieChart>
          </ResponsiveContainer>
        </div>
        <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
          <span className="text-muted-foreground text-xl font-semibold">
            {total}
          </span>
          <span className="text-muted-foreground text-xs uppercase">
            análises
          </span>
        </div>
      </div>
      <ul className="flex w-full flex-1 flex-col gap-3">
        {chartData.map((entry) => (
          <li key={entry.category} className="flex items-center gap-3 text-sm">
            <span
              className="h-2.5 w-2.5 shrink-0 rounded-full"
              style={{ backgroundColor: entry.fill }}
            />
            <span className="text-foreground sm:text-base">{entry.name}</span>
            <Divider orientation="horizontal" className="flex-1" />
            <span className="text-muted-foreground whitespace-nowrap">{`${entry.value} (${formatPercent((entry.value / total) * 100)})`}</span>
          </li>
        ))}
      </ul>
    </div>
  )
}
