import { z } from 'zod'

export const analysisSummarySchema = z.object({
  total_analises: z.number().int().nonnegative(),
  media_custo_mensal: z.number().nonnegative(),
  media_consumo_kwh: z.number().nonnegative(),
  total_eficiente: z.number().int().nonnegative(),
  total_moderado: z.number().int().nonnegative(),
  total_ineficiente: z.number().int().nonnegative(),
})

export type AnalysisSummaryResponse = z.infer<typeof analysisSummarySchema>
