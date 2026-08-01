import z from 'zod'

import { historyItemSchema } from './historyItem'

export const analysisHistorySchema = z.object({
  analises: z.array(historyItemSchema),
  pagina_atual: z.number().int(),
  tamanho_pagina: z.number().int(),
  total_elementos: z.number().int(),
  total_paginas: z.number().int(),
})

export type AnalysisHistory = z.infer<typeof analysisHistorySchema>
