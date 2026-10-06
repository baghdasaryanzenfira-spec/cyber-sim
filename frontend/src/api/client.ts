import axios, { AxiosError } from 'axios'
import type { ApiError } from './types'

const TOKEN_KEY = 'cybersim.token'

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

/** Axios instance: same-origin /api (proxied to the backend), JWT attached to every request. */
export const http = axios.create({ baseURL: '/api', timeout: 60_000 })

http.interceptors.request.use((config) => {
  const token = tokenStore.get()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

let onUnauthorized: (() => void) | null = null

/** AuthContext registers a callback so that an expired token logs the user out everywhere. */
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler
}

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiError>) => {
    if (error.response?.status === 401 && !error.config?.url?.startsWith('/auth/login')) {
      tokenStore.clear()
      onUnauthorized?.()
    }
    return Promise.reject(error)
  },
)

/** Human-readable message from any API error (uses the backend's uniform ApiError body). */
export function errorMessage(error: unknown): string {
  if (axios.isAxiosError<ApiError>(error)) {
    const body = error.response?.data
    if (body?.fieldErrors?.length) {
      return `${body.message}: ${body.fieldErrors.map((f) => `${f.field} ${f.message}`).join('; ')}`
    }
    if (body?.message) return body.message
    if (!error.response) return 'The server is not reachable.'
  }
  return 'Unexpected error'
}

export function apiErrorBody(error: unknown): ApiError | undefined {
  return axios.isAxiosError<ApiError>(error) ? error.response?.data : undefined
}
