import { useAsync } from '@/shared/hooks/useAsync'

import {
  getAnalysisHistory,
  type GetAnalysisHistoryParams,
} from '../api/services/historyService'

export function useAnalysisHistory(params: GetAnalysisHistoryParams = {}) {
  return useAsync(
    () => getAnalysisHistory(params),
    [params.page, params.size, params.sort],
    {
      errorMessage: 'Não foi possível carregar o histórico de análises.',
    },
  )
}
