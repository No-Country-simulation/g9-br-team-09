import { useAsync } from '@/shared/hooks/useAsync'

import { getAnalysisDetail } from '../api/services/detailsService'

export function useAnalysisDetails(id: number | null) {
  return useAsync(
    () => (id === null ? Promise.resolve(null) : getAnalysisDetail(id)),
    [id],
    {
      errorMessage: 'Não foi possível carregar os detalhes da análise.',
    },
  )
}
