export interface RegisterRequest {
  nome: string
  email: string
  senha: string
}

export interface LoginRequest {
  email: string
  senha: string
}
