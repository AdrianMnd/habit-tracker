<script setup lang="ts">
</script>

<template>
  <div class="app">
    <header class="app-header">
      <h1 class="app-header__title">Habit Tracker</h1>
      <nav class="app-header__nav">
        <RouterLink to="/">Mis hábitos</RouterLink>
      </nav>
    </header>

    <main class="app-main">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.app-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  padding: var(--space-6) var(--space-8);
  border-bottom: 1px solid var(--color-stone);
}

.app-header__title {
  font-size: 1.5rem;
}

.app-header__nav a {
  font-size: 0.9rem;
  color: var(--color-ink-soft);
}

.app-header__nav a.router-link-active {
  color: var(--color-moss);
}

.app-main {
  max-width: 960px;
  margin: 0 auto;
  padding: var(--space-8);
}
</style>
