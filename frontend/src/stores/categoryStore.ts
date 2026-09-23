import { defineStore } from 'pinia'
import { ref } from 'vue'
import { categoryApi } from '@/services/categoryApi'
import type { Category, CategoryRequest } from '@/types/category'

export const useCategoryStore = defineStore('categories', () => {
  const categories = ref<Category[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchCategories() {
    loading.value = true
    error.value = null
    try {
      categories.value = await categoryApi.getAll()
    } catch (e) {
      error.value = e instanceof Error ? e.message : 'Error cargando categorias'
    } finally {
      loading.value = false
    }
  }

  async function addCategory(data: CategoryRequest) {
    const created = await categoryApi.create(data)
    categories.value.push(created)
    categories.value.sort((a, b) => a.name.localeCompare(b.name))
    return created
  }

  async function removeCategory(id: number) {
    await categoryApi.remove(id)
    categories.value = categories.value.filter((c) => c.id !== id)
  }

  return { categories, loading, error, fetchCategories, addCategory, removeCategory }
})
