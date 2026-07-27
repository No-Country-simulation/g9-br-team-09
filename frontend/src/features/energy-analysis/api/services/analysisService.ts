import { httpClient } from '@/shared/api/httpClient'

import type { AnalysisFormData } from '../../data/analysis'
import { createAnalysisRequestSchema } from '../../schemas/analysis'
import type { CreateAnalysisResponse } from '../../types/analysis'

const RESOURCE = '/analise-energetica'

export async function createAnalysis(data: AnalysisFormData) {
  const requestPayload = createAnalysisRequestSchema.parse(data)
  const response = await httpClient.post<CreateAnalysisResponse>(
    RESOURCE,
    requestPayload,
  )
  return response.data
}
