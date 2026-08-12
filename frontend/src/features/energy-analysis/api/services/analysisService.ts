import { httpClient } from '@/shared/api/httpClient'
import { ENERGY_ANALYSIS_RESOURCE } from '@/shared/api/resources'

import type { AnalysisFormData } from '../../data/analysis'
import { createAnalysisRequestSchema } from '../../schemas/analysis'
import type { CreateAnalysisResponse } from '../../types/analysis'

export async function createAnalysis(data: AnalysisFormData) {
  const requestPayload = createAnalysisRequestSchema.parse(data)
  const response = await httpClient.post<CreateAnalysisResponse>(
    ENERGY_ANALYSIS_RESOURCE,
    requestPayload,
  )
  return response.data
}
