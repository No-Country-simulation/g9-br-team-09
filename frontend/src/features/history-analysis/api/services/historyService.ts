import { httpClient } from '@/shared/api/httpClient'
import { ENERGY_ANALYSIS_RESOURCE } from '@/shared/api/resources'

import { analysisHistorySchema } from '../../schemas/analysisHistory'

export interface GetAnalysisHistoryParams {
  page?: number
  size?: number
  sort?: string
}

export async function getAnalysisHistory(
  params: GetAnalysisHistoryParams = {},
) {
  const response = await httpClient.get(ENERGY_ANALYSIS_RESOURCE, {
    params: {
      page: params.page ?? 0,
      size: params.size ?? 20,
      sort: params.sort ?? 'createdAt,DESC',
    },
  })

  return analysisHistorySchema.parse(response.data)
}
