import { z } from 'zod'

import { authEmailSchema, authPasswordSchema } from './common'

export const loginSchema = z.object({
  email: authEmailSchema,
  password: authPasswordSchema,
})

export type LoginFormValues = z.infer<typeof loginSchema>
