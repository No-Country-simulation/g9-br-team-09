import type z from 'zod'

import type {
  categorySchema,
  classificationSourceSchema,
} from '../schemas/analysis'

export type Category = z.infer<typeof categorySchema>
export type ClassificationSource = z.infer<typeof classificationSourceSchema>
