import { useAsync } from '@/shared/hooks/useAsync'

import { getAnalysisSummary } from '../api/services/dashboardService'

export function useAnalysisSummary() {
  return useAsync(getAnalysisSummary, [], {
    errorMessage: 'Não foi possível carregar o resumo das análises.',
  })
}
