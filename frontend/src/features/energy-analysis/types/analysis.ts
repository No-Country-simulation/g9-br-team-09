export interface CreateAnalysisResponse {
  id: number
  categoria: 'EFICIENTE' | 'MODERADO' | 'INEFICIENTE'
  probabilidade: number
  custo_estimado_mensal: number
  recomendacoes: string[]
  fonte_classificacao: 'RULE_BASED' | 'ML_MODEL' | 'RULE_BASED_FALLBACK'
}
