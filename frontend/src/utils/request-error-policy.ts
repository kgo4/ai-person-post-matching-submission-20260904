import type { AxiosRequestConfig } from 'axios'

export function shouldShowRequestErrorToast(config?: AxiosRequestConfig): boolean {
  return config?.showErrorToast === true
}
