import { apiRequest } from '@/services/http'
import type { Category, CategoryRequest } from '@/types/category'

export const categoryApi = {
  getAll: () => apiRequest<Category[]>('/categories'),

  create: (data: CategoryRequest) =>
    apiRequest<Category>('/categories', { method: 'POST', body: JSON.stringify(data) }),

  remove: (id: number) => apiRequest<void>(`/categories/${id}`, { method: 'DELETE' })
}
