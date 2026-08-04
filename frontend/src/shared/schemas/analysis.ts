import { z } from 'zod'

export const propertyTypeSchema = z.enum([
  'CASA',
  'APARTAMENTO',
  'COMERCIO',
  'ESCRITORIO',
  'INDUSTRIA',
  'OUTRO',
])

export const categorySchema = z.enum(['EFICIENTE', 'MODERADO', 'INEFICIENTE'])
export const classificationSourceSchema = z.enum([
  'RULE_BASED',
  'ML_MODEL',
  'RULE_BASED_FALLBACK',
])

export const analysisClassificationSchema = z.object({
  categoria: categorySchema,
  probabilidade: z.number().min(0).max(1),
  score: z.number().int().min(0).max(100),
  custo_estimado_mensal: z.number(),
  recomendacoes: z.array(z.string()),
  fonte_classificacao: classificationSourceSchema,
})
