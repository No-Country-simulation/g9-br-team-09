import type {
  Category,
  ClassificationSource,
  PropertyType,
} from '../types/analysis'
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

export const propertyTypeLabels: Record<PropertyType, string> = {
  CASA: 'Casa',
  APARTAMENTO: 'Apartamento',
  COMERCIO: 'Comércio',
  ESCRITORIO: 'Escritório',
  INDUSTRIA: 'Indústria',
  OUTRO: 'Outros',
}
