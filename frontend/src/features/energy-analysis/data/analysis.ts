import { ClockArrowUp, Home, Hourglass, Tv, Zap } from 'lucide-react'

import type { FormStepProps } from '../components/FormStep'
import {
  ANALYSIS_FIELD_LIMITS,
  BOOLEAN_RADIO_VALUES,
  PROPERTY_TYPE_VALUES,
} from '../schemas/analysis'
export type { AnalysisFormData } from '../schemas/analysis'

const propertyTypeLabels: Record<
  (typeof PROPERTY_TYPE_VALUES)[number],
  string
> = {
  CASA: 'Casa',
  APARTAMENTO: 'Apartamento',
  COMERCIO: 'Comércio',
  ESCRITORIO: 'Escritório',
  INDUSTRIA: 'Indústria',
  OUTRO: 'Outros',
}

const booleanRadioLabels: Record<
  (typeof BOOLEAN_RADIO_VALUES)[number],
  string
> = {
  true: 'Sim',
  false: 'Não',
}

export const analysisFormSteps = [
  {
    id: 'propertyType',
    icon: Home,
    title: 'Tipo imóvel',
    question: 'Qual é o tipo do imóvel que será analisado?',
    field: {
      kind: 'radio',
      options: PROPERTY_TYPE_VALUES.map((value) => ({
        label: propertyTypeLabels[value],
        value,
      })),
    },
  },
  {
    id: 'applianceCount',
    icon: Tv,
    title: 'Equipamentos elétricos',
    question: 'Quantos equipamentos elétricos existem no imóvel?',
    description: 'Considere eletrodomésticos e eletrônicos de uso frequente.',
    field: {
      kind: 'input',
      inputProps: {
        type: 'number',
        step: 1,
        inputMode: 'numeric',
        placeholder: 'ex: 10',
        suffix: 'equipamentos',
        min: ANALYSIS_FIELD_LIMITS.applianceCount.min,
        max: ANALYSIS_FIELD_LIMITS.applianceCount.max,
      },
    },
  },
  {
    id: 'monthlyConsumption',
    icon: Zap,
    title: 'Consumo mensal',
    question: 'Qual foi o consumo de energia no último mês?',
    field: {
      kind: 'input',
      inputProps: {
        type: 'number',
        inputMode: 'decimal',
        placeholder: 'ex: 420',
        suffix: 'kWh',
        min: ANALYSIS_FIELD_LIMITS.monthlyConsumption.min,
        max: ANALYSIS_FIELD_LIMITS.monthlyConsumption.max,
      },
    },
  },
  {
    id: 'peakUsage',
    icon: ClockArrowUp,
    title: 'Horário de pico',
    question:
      'Você costuma utilizar aparelhos de maior consumo no horário de pico?',
    description: 'O horário de pico geralmente ocorre entre 18h e 21h.',
    field: {
      kind: 'radio',
      options: BOOLEAN_RADIO_VALUES.map((value) => ({
        label: booleanRadioLabels[value],
        value,
      })),
    },
  },
  {
    id: 'peakConsumptionHours',
    icon: Hourglass,
    title: 'Horas de maior consumo',
    question:
      'Quantas horas por dia os equipamentos de maior consumo permanecem em uso?',
    field: {
      kind: 'input',
      inputProps: {
        type: 'number',
        step: 1,
        inputMode: 'numeric',
        placeholder: 'ex: 4',
        suffix: 'horas',
        min: ANALYSIS_FIELD_LIMITS.peakConsumptionHours.min,
        max: ANALYSIS_FIELD_LIMITS.peakConsumptionHours.max,
      },
    },
    submitButtonProps: {
      label: 'Gerar análise',
    },
  },
] satisfies FormStepProps[]
