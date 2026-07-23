import { useState } from 'react'

import { createAnalysis } from '../api/services/analysisService'
import type { AnalysisFormData } from '../data/analysis'

export function useCreateAnalysis() {
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const submit = async (data: AnalysisFormData) => {
    setIsSubmitting(true)
    setError(null)
    try {
      return await createAnalysis(data)
    } catch {
      setError('Não foi possível enviar sua análise. Tente novamente.')
      return null
    } finally {
      setIsSubmitting(false)
    }
  }
  return { submit, isSubmitting, error }
}
