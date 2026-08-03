import z from 'zod'

import { categorySchema } from '@/shared/schemas/analysis'

export const historyItemSchema = z.object({
  id: z.number().int(),
  categoria: categorySchema,
  probabilidade: z.number().min(0).max(1),
  score: z.number().int().min(0).max(100),
  custo_estimado_mensal: z.number(),
  criado_em: z.string(),
})

export type HistoryItem = z.infer<typeof historyItemSchema>
