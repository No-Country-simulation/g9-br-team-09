import axios from 'axios'
import { z } from 'zod'

export const apiErrorSchema = z.object({
  timestamp: z.string().min(1),
  status: z.number().int(),
  error: z.string().min(1),
  message: z.string().min(1),
})

export type ApiErrorResponse = z.infer<typeof apiErrorSchema>

export type NormalizedApiError =
  | { kind: 'http'; status: number; response?: ApiErrorResponse }
  | { kind: 'network' }
  | { kind: 'unexpected' }

export function normalizeApiError(error: unknown): NormalizedApiError {
  if (!axios.isAxiosError(error)) {
    return { kind: 'unexpected' }
  }

  if (!error.response) {
    return { kind: 'network' }
  }

  const parsedResponse = apiErrorSchema.safeParse(error.response.data)

  return {
    kind: 'http',
    status: error.response.status,
    response: parsedResponse.success ? parsedResponse.data : undefined,
  }
}
