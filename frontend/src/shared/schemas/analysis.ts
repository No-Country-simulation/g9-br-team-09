import { z } from 'zod'

export const categorySchema = z.enum(['EFICIENTE', 'MODERADO', 'INEFICIENTE'])
export const classificationSourceSchema = z.enum([
  'RULE_BASED',
  'ML_MODEL',
  'RULE_BASED_FALLBACK',
])
