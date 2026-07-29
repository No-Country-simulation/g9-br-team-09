import type { Category, ClassificationSource } from '@/shared/types/analysis'

export interface CreateAnalysisResponse {
  id: number
  categoria: Category
  score: number
  probabilidade: number
  custo_estimado_mensal: number
  recomendacoes: string[]
  fonte_classificacao: ClassificationSource
}
