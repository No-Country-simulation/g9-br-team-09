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
  applianceCount: { min: 1, max: 500 },
  monthlyConsumption: { min: 1, max: 5000 },
  peakConsumptionHours: { min: 0, max: 24 },
} as const

const numericStringSchema = (
  fieldLabel: string,
  min: number,
  max: number,
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
      integer
        ? z
            .number({ message: `${fieldLabel} deve ser um número válido` })
            .int(`${fieldLabel} deve ser um número inteiro`)
            .min(min, `${fieldLabel} deve estar entre ${min} e ${max}`)
            .max(max, `${fieldLabel} deve estar entre ${min} e ${max}`)
        : z
            .number({ message: `${fieldLabel} deve ser um número válido` })
            .min(min, `${fieldLabel} deve estar entre ${min} e ${max}`)
            .max(max, `${fieldLabel} deve estar entre ${min} e ${max}`),
    )

export const analysisFieldSchemas = {
  propertyType: z.enum(PROPERTY_TYPE_VALUES, {
    message: 'Selecione um tipo de imóvel',
  }),
  applianceCount: numericStringSchema(
    'A quantidade de equipamentos',
    ANALYSIS_FIELD_LIMITS.applianceCount.min,
    ANALYSIS_FIELD_LIMITS.applianceCount.max,
    true,
  ),
  monthlyConsumption: numericStringSchema(
    'O consumo mensal',
    ANALYSIS_FIELD_LIMITS.monthlyConsumption.min,
    ANALYSIS_FIELD_LIMITS.monthlyConsumption.max,
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
