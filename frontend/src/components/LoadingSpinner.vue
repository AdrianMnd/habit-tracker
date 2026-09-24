<script setup lang="ts">
defineProps<{
  label?: string
}>()
</script>

<template>
  <div class="loading-spinner">
    <span class="spinner" aria-hidden="true"></span>
    <span v-if="label" class="spinner-label">{{ label }}</span>
  </div>
</template>

<style scoped>
.loading-spinner {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-6) 0;
  color: var(--color-ink-soft);
  font-size: 0.9rem;
}

.spinner {
  width: 20px;
  height: 20px;
  border: 2px solid var(--color-stone);
  border-top-color: var(--color-moss);
  border-radius: 50%;
  /* La regla global de main.css (@media prefers-reduced-motion) ya
     desactiva cualquier animation en todo el sitio con "!important" -
     no hace falta repetir esa condicion aqui, ya la hereda sola. */
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
