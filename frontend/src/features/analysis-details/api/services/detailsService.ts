import axios from 'axios'

import { httpClient } from '@/shared/api/httpClient'
import { ENERGY_ANALYSIS_RESOURCE } from '@/shared/api/resources'

import { analysisDetailSchema } from '../../schemas/analysisDetails'

export async function getAnalysisDetail(id: number) {
  try {
    const response = await httpClient.get(`${ENERGY_ANALYSIS_RESOURCE}/${id}`)
    return analysisDetailSchema.parse(response.data)
  } catch (err) {
    if (axios.isAxiosError(err) && err.response?.status === 404) {
      return null
    }
    throw err
  }
}
