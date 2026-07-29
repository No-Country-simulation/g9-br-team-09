import { z } from 'zod'

export const PROPERTY_TYPE_VALUES = [
  'CASA',
  'APARTAMENTO',
  'COMERCIO',
  'ESCRITORIO',
  'INDUSTRIA',
  'OUTRO',
] as const

export const BOOLEAN_RADIO_VALUES = ['true', 'false'] as const

export const ANALYSIS_FIELD_LIMITS = {
  applianceCount: { min: 1 },
  monthlyConsumption: { min: 1 },
  peakConsumptionHours: { min: 0, max: 24 },
} as const

const numericStringSchema = (
  fieldLabel: string,
  min: number,
  max?: number,
  integer = false,
) =>
  z
    .string()
    .trim()
    .min(1, `${fieldLabel} não deve ser vazio`)
    .transform((value, ctx) => {
      const parsedValue = Number(value)

      if (Number.isNaN(parsedValue)) {
        ctx.addIssue({
          code: 'custom',
          message: `${fieldLabel} deve conter apenas números`,
        })
        return z.NEVER
      }

      return parsedValue
    })
    .pipe(
      (() => {
        const hasMax = typeof max === 'number'
        const rangeMessage = hasMax
          ? `${fieldLabel} deve estar entre ${min} e ${max}`
          : `${fieldLabel} deve ser maior ou igual a ${min}`

        const baseNumber = z.number({
          message: `${fieldLabel} deve ser um número válido`,
        })

        if (integer) {
          let schema = baseNumber
            .int(`${fieldLabel} deve ser um número inteiro`)
            .min(min, rangeMessage)
          if (hasMax) schema = schema.max(max as number, rangeMessage)
          return schema
        }

        let schema = baseNumber.min(min, rangeMessage)
        if (hasMax) schema = schema.max(max as number, rangeMessage)
        return schema
      })(),
    )

export const analysisFieldSchemas = {
  propertyType: z.enum(PROPERTY_TYPE_VALUES, {
    message: 'Selecione um tipo de imóvel',
  }),
  applianceCount: numericStringSchema(
    'A quantidade de equipamentos',
    ANALYSIS_FIELD_LIMITS.applianceCount.min,
    undefined,
    true,
  ),
  monthlyConsumption: numericStringSchema(
    'O consumo mensal',
    ANALYSIS_FIELD_LIMITS.monthlyConsumption.min,
  ),
  peakUsage: z.enum(BOOLEAN_RADIO_VALUES, {
    message: 'Selecione uma opção',
  }),
  peakConsumptionHours: numericStringSchema(
    'As horas de alto consumo',
    ANALYSIS_FIELD_LIMITS.peakConsumptionHours.min,
    ANALYSIS_FIELD_LIMITS.peakConsumptionHours.max,
    true,
  ),
} as const

export const analysisFormValuesSchema = z.object(analysisFieldSchemas)

export const createAnalysisRequestSchema = analysisFormValuesSchema.transform(
  ({
    monthlyConsumption,
    peakConsumptionHours,
    applianceCount,
    propertyType,
    peakUsage,
  }) => ({
    consumo_kwh: monthlyConsumption,
    horas_alto_consumo: peakConsumptionHours,
    quantidade_equipamentos: applianceCount,
    tipo_imovel: propertyType,
    uso_horario_pico: peakUsage === 'true',
  }),
)

export type AnalysisFormData = z.input<typeof analysisFormValuesSchema>
export type AnalysisFieldKey = keyof typeof analysisFieldSchemas
export type CreateAnalysisRequest = z.infer<typeof createAnalysisRequestSchema>
