import { ArrowLeft, ArrowRight, type LucideIcon } from 'lucide-react'
import { type SyntheticEvent, useState } from 'react'

import { Button } from '@/shared/components/Button'

import {
  type AnalysisFieldKey,
  analysisFieldSchemas,
} from '../schemas/analysis'
import { Input, type InputProps } from './Input'
import { RadioCard } from './RadioCard'

interface DefaultValueProps {
  defaultValue?: string
}

interface RadioOption {
  label: string
  value: string
}

type FieldConfig =
  | {
      kind: 'input'
      inputProps: Omit<InputProps, 'value' | 'onChange'>
    }
  | {
      kind: 'radio'
      options: RadioOption[]
    }

export interface FormStepProps {
  id: AnalysisFieldKey
  icon: LucideIcon
  title: string
  question: string
  description?: string
  field: FieldConfig
  submitButtonProps?: {
    label: string
  }
}

interface StepPositionProps {
  currentStep: number
  totalSteps: number
}

interface ActionsButtonsProps {
  onBack: () => void
  onNext: (value: string) => void
  hideBackButton?: boolean
  isSubmitting?: boolean
}

export function FormStep({
  id,
  icon: Icon,
  title,
  question,
  description,
  field,
  submitButtonProps,
  currentStep,
  totalSteps,
  onBack,
  onNext,
  hideBackButton,
  isSubmitting,
  defaultValue,
}: FormStepProps &
  StepPositionProps &
  ActionsButtonsProps &
  DefaultValueProps) {
  const [inputValue, setInputValue] = useState(defaultValue ?? '')
  const [fieldError, setFieldError] = useState<string | null>(null)
  const handleSubmit = (e: SyntheticEvent<HTMLFormElement>) => {
    e.preventDefault()
    const validationResult = analysisFieldSchemas[id].safeParse(inputValue)

    if (!validationResult.success) {
      setFieldError(
        validationResult.error.issues[0]?.message ?? 'Valor inválido',
      )
      return
    }

    setFieldError(null)
    onNext(String(validationResult.data))
  }
  return (
    <div className="bg-card border-border sm:shadow-card mt-10 rounded-2xl border p-6 sm:border-0 sm:p-8">
      <div className="mb-4 flex items-center justify-between">
        <div className="text-primary flex items-center gap-1.5 text-xs font-semibold uppercase tracking-widest ">
          <Icon size={16} />
          {title}
        </div>
        <span className="text-muted-foreground text-xs">
          Etapa {currentStep} de {totalSteps}
        </span>
      </div>
      <form
        noValidate
        onSubmit={handleSubmit}
        aria-busy={isSubmitting}
        className="mt-6 flex flex-col gap-4"
      >
        <fieldset className="flex flex-col border-0 p-0">
          <legend className="text-foreground text-xl font-semibold leading-snug">
            {question}
          </legend>
          {description && (
            <p className="text-muted-foreground text-sm">{description}</p>
          )}
          {field.kind === 'input' ? (
            <div className="mt-6">
              <Input
                {...field.inputProps}
                value={inputValue}
                aria-invalid={Boolean(fieldError)}
                aria-describedby={fieldError ? `${id}-error` : undefined}
                onChange={(event) => {
                  setInputValue(event.target.value)
                  if (fieldError) setFieldError(null)
                }}
              />
            </div>
          ) : (
            <div className="mt-4 grid grid-cols-2 gap-3">
              {field.options.map((option) => (
                <RadioCard
                  key={option.value}
                  name={id}
                  label={option.label}
                  value={option.value}
                  checked={inputValue === option.value}
                  aria-invalid={Boolean(fieldError)}
                  aria-describedby={fieldError ? `${id}-error` : undefined}
                  onChange={() => {
                    setInputValue(option.value)
                    if (fieldError) setFieldError(null)
                  }}
                />
              ))}
            </div>
          )}
          {fieldError && (
            <p
              id={`${id}-error`}
              className="text-destructive text-sm text-red-500"
              role="alert"
            >
              {fieldError}
            </p>
          )}
        </fieldset>
        <div className="flex flex-col gap-3 sm:flex-row sm:gap-6">
          {!hideBackButton && (
            <Button
              onClick={onBack}
              variant="ghost"
              icon={ArrowLeft}
              className="rouded-xl order-2 flex-1 justify-center py-3 sm:order-1"
              disabled={isSubmitting}
            >
              Voltar
            </Button>
          )}
          <Button
            type="submit"
            variant="primary"
            icon={!submitButtonProps ? ArrowRight : undefined}
            disabled={!inputValue}
            className="order-1 flex-1 sm:order-2"
          >
            {isSubmitting
              ? 'Enviando...'
              : (submitButtonProps?.label ?? 'Próximo')}
          </Button>
        </div>
      </form>
    </div>
  )
}
