import type { ChangeEvent, SubmitEvent } from 'react'
import { useState } from 'react'
import { z } from 'zod'

import type { FormErrors } from '../types/form'

type AuthFormValues = Record<string, string>

function mapFieldErrors<TValues extends AuthFormValues>(
  fieldErrors: Partial<Record<string, string[]>>,
): FormErrors<TValues> {
  return Object.entries(fieldErrors).reduce<FormErrors<TValues>>(
    (accumulator, [field, messages]) => {
      const message = messages?.[0]

      if (message) {
        accumulator[field as keyof TValues] = message
      }

      return accumulator
    },
    {},
  )
}

interface UseAuthFormOptions<TValues extends AuthFormValues> {
  initialValues: TValues
  schema: z.ZodType<TValues>
  onValidSubmit?: (values: TValues) => Promise<void> | void
}

export function useAuthForm<TValues extends AuthFormValues>({
  initialValues,
  schema,
  onValidSubmit,
}: UseAuthFormOptions<TValues>) {
  const [values, setValues] = useState<TValues>(initialValues)
  const [errors, setErrors] = useState<FormErrors<TValues>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  function handleChange(field: keyof TValues) {
    return (event: ChangeEvent<HTMLInputElement>) => {
      const nextValue = event.target.value

      setValues((previousValues) => ({
        ...previousValues,
        [field]: nextValue,
      }))

      setErrors((previousErrors) => {
        if (!previousErrors[field]) {
          return previousErrors
        }

        const nextErrors = { ...previousErrors }
        delete nextErrors[field]
        return nextErrors
      })
    }
  }

  async function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault()

    setFormError(null)

    const validationResult = schema.safeParse(values)

    if (!validationResult.success) {
      const { fieldErrors } = z.flattenError(validationResult.error)
      setErrors(mapFieldErrors<TValues>(fieldErrors))
      return
    }

    setErrors({})

    if (!onValidSubmit) {
      return
    }

    setIsSubmitting(true)
    try {
      await onValidSubmit(validationResult.data)
    } finally {
      setIsSubmitting(false)
    }
  }

  return {
    errors,
    formError,
    handleChange,
    handleSubmit,
    isSubmitting,
    setErrors,
    setFormError,
    values,
  }
}
