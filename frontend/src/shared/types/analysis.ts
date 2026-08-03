import type z from 'zod'

import type {
  analysisClassificationSchema,
  categorySchema,
  classificationSourceSchema,
  propertyTypeSchema,
} from '../schemas/analysis'

export type PropertyType = z.infer<typeof propertyTypeSchema>
export type Category = z.infer<typeof categorySchema>
export type ClassificationSource = z.infer<typeof classificationSourceSchema>
export type AnalysisClassification = z.infer<
  typeof analysisClassificationSchema
>
