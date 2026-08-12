import axios from 'axios'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL

const sharedConfig = {
  baseURL: API_BASE_URL,
  withCredentials: true,
  withXSRFToken: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
}

export const httpClient = axios.create(sharedConfig)

// Auth requests bypass the main response interceptor so refresh cannot retry itself.
export const authHttpClient = axios.create(sharedConfig)
