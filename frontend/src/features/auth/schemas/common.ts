import { z } from 'zod'

export const authEmailSchema = z
  .email('Informe um e-mail válido.')
  .max(255, 'O e-mail deve ter no máximo 255 caracteres.')

export const authPasswordSchema = z
  .string()
  .min(8, 'A senha deve ter ao menos 8 caracteres.')
  .max(100, 'A senha deve ter no máximo 100 caracteres.')
