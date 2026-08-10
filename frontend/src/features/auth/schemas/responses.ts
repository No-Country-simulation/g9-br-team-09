import { z } from 'zod'

export const authenticatedUserSchema = z.object({
  id: z.number().int().positive(),
  nome: z.string().min(1),
  email: z.string().email(),
  role: z.literal('USER'),
  criado_em: z.string().min(1),
})

export const registrationResponseSchema = authenticatedUserSchema

export const authenticationResponseSchema = z.object({
  access_token: z.string().min(1),
  token_type: z.literal('Bearer'),
  expires_in: z.number().int().positive(),
  usuario: authenticatedUserSchema,
})

export type AuthenticatedUser = z.infer<typeof authenticatedUserSchema>
export type RegistrationResponse = z.infer<typeof registrationResponseSchema>
export type AuthenticationResponse = z.infer<typeof authenticationResponseSchema>
