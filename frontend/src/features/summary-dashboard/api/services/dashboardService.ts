import { httpClient } from '@/shared/api/httpClient'
import { ENERGY_ANALYSIS_RESOURCE } from '@/shared/api/resources'

import { analysisSummarySchema } from '../../schemas/analysisSummary'

export async function getAnalysisSummary() {
  const response = await httpClient.get(`${ENERGY_ANALYSIS_RESOURCE}/resumo`)

  const result = analysisSummarySchema.safeParse(response.data)
  if (!result.success) {
    console.error('Resposta inválida em /resumo:', result.error)
    throw new Error('Resposta da API em formato inesperado')
  }

  return result.data
}
