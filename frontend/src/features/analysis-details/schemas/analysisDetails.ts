import z from 'zod'

import {
  categorySchema,
  classificationSourceSchema,
  propertyTypeSchema,
} from '@/shared/schemas/analysis'

export const analysisDetailSchema = z.object({
  id: z.number().int(),
  consumo_kwh: z.number(),
  uso_horario_pico: z.boolean(),
  quantidade_equipamentos: z.number().int(),
  tipo_imovel: propertyTypeSchema,
  horas_alto_consumo: z.number().int().min(0).max(24),
  categoria: categorySchema,
  probabilidade: z.number().min(0).max(1),
  score: z.number().int().min(0).max(100),
  custo_estimado_mensal: z.number(),
  recomendacoes: z.array(z.string()),
  fonte_classificacao: classificationSourceSchema,
  criado_em: z.string(),
})

export type AnalysisDetail = z.infer<typeof analysisDetailSchema>
