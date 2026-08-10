import { z } from 'zod'

import { authEmailSchema, authPasswordSchema } from './common'

export const registerSchema = z
  .object({
    fullName: z
      .string()
      .trim()
      .nonempty('O nome é obrigatório.')
      .max(255, 'O nome deve ter no máximo 255 caracteres.'),
    email: authEmailSchema,
    password: authPasswordSchema,
    confirmPassword: authPasswordSchema,
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: 'As senhas não coincidem.',
    path: ['confirmPassword'],
  })

export type RegisterFormValues = z.infer<typeof registerSchema>

export interface RegisterRequest {
  nome: string
  email: string
  senha: string
}

export function toRegisterRequest({
  fullName,
  email,
  password,
}: RegisterFormValues): RegisterRequest {
  return {
    nome: fullName,
    email,
    senha: password,
  }
}
