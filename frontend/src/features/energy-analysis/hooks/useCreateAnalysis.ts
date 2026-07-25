import { useRef, useState } from 'react'

import { createAnalysis } from '../api/services/analysisService'
import type { AnalysisFormData } from '../data/analysis'

export function useCreateAnalysis() {
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const isSubmittingRef = useRef(false)

  const submit = async (data: AnalysisFormData) => {
    if (isSubmittingRef.current) return null

    isSubmittingRef.current = true
    setIsSubmitting(true)
    setError(null)
    try {
      return await createAnalysis(data)
    } catch {
      setError('Não foi possível enviar sua análise. Tente novamente.')
      return null
    } finally {
      isSubmittingRef.current = false
      setIsSubmitting(false)
    }
  }
  return { submit, isSubmitting, error }
}
