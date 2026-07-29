import { httpClient } from '@/shared/api/httpClient'
import { ENERGY_ANALYSIS_RESOURCE } from '@/shared/api/resources'

import type { AnalysisSummaryResponse } from '../../types/analysisSummary'

export async function getAnalysisSummary() {
  const response = await httpClient.get<AnalysisSummaryResponse>(
    `${ENERGY_ANALYSIS_RESOURCE}/resumo`,
  )
  return response.data
}
