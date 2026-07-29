import type { Category, ClassificationSource } from '../types/analysis'
import type { Variant } from './cardStyles'

export const categoryLabels: Record<Category, string> = {
  EFICIENTE: 'Eficiente',
  MODERADO: 'Moderado',
  INEFICIENTE: 'Ineficiente',
}

export const categoryVariants: Record<Category, Variant> = {
  EFICIENTE: 'efficient',
  MODERADO: 'moderate',
  INEFICIENTE: 'inefficient',
}

export const sourceLabels: Record<ClassificationSource, string> = {
  RULE_BASED: 'Regra de negócio',
  ML_MODEL: 'Modelo preditivo',
  RULE_BASED_FALLBACK: 'Critério de reserva',
}
