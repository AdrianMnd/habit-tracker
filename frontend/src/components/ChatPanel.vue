<script setup lang="ts">
import { ref } from 'vue'
import { useHabitChat } from '@/composables/useHabitChat'
import { useHabitStore } from '@/stores/habitStore'
import type { HabitSuggestion } from '@/types/habit'

const { messages, sending, error, sendMessage } = useHabitChat()
const habitStore = useHabitStore()

const draft = ref('')
const addedNames = ref(new Set<string>())

async function handleSend() {
  const text = draft.value
  draft.value = ''
  await sendMessage(text)
}

async function handleAddSuggestion(suggestion: HabitSuggestion) {
  await habitStore.addHabit({ name: suggestion.name, description: suggestion.description })
  addedNames.value.add(suggestion.name)
}
</script>

<template>
  <section class="margin-note">
    <h3>Consulta a la IA</h3>
    <p class="margin-note__hint">
      Cuéntame un objetivo ("dormir mejor", "ser más productivo") o pregúntame
      sobre tus hábitos actuales.
    </p>

    <div class="chat-log">
      <div
        v-for="(message, index) in messages"
        :key="index"
        class="chat-message"
        :class="`chat-message--${message.role}`"
      >
        <p>{{ message.text }}</p>

        <ul v-if="message.suggestions?.length" class="suggestion-list">
          <li v-for="suggestion in message.suggestions" :key="suggestion.name" class="suggestion">
            <div class="suggestion__info">
              <strong>{{ suggestion.name }}</strong>
              <span v-if="suggestion.description">{{ suggestion.description }}</span>
            </div>
            <button
              type="button"
              class="suggestion__add"
              :disabled="addedNames.has(suggestion.name)"
              @click="handleAddSuggestion(suggestion)"
            >
              {{ addedNames.has(suggestion.name) ? 'Añadido' : 'Añadir' }}
            </button>
          </li>
        </ul>
      </div>

      <p v-if="sending && !messages[messages.length - 1]?.text" class="chat-status">Pensando...</p>
      <p v-if="error" class="chat-status chat-status--error">{{ error }}</p>
    </div>

    <form class="chat-form" @submit.prevent="handleSend">
      <input v-model="draft" placeholder="Escribe tu mensaje..." :disabled="sending" />
      <button type="submit" :disabled="sending || !draft.trim()">Enviar</button>
    </form>
  </section>
</template>

<style scoped>
.margin-note {
  border-left: 2px solid var(--color-ember);
  padding-left: var(--space-4);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.margin-note h3 {
  font-size: 1rem;
}

.margin-note__hint {
  font-size: 0.85rem;
  color: var(--color-ink-soft);
  margin: 0;
}

.chat-log {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  max-height: 360px;
  overflow-y: auto;
}

.chat-message p {
  margin: 0;
  font-size: 0.9rem;
  line-height: 1.4;
}

.chat-message--user p {
  color: var(--color-ink-soft);
}

.chat-message--assistant p {
  color: var(--color-ink);
}

.suggestion-list {
  list-style: none;
  margin: var(--space-2) 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.suggestion {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: var(--space-2);
  background: var(--color-paper-raised);
  border: 1px solid var(--color-stone);
  border-radius: 6px;
  padding: var(--space-2) var(--space-3);
}

.suggestion__info {
  display: flex;
  flex-direction: column;
  font-size: 0.85rem;
}

.suggestion__info span {
  color: var(--color-ink-soft);
  font-size: 0.8rem;
}

.suggestion__add {
  flex-shrink: 0;
  border: 1px solid var(--color-moss);
  background: transparent;
  color: var(--color-moss);
  border-radius: 999px;
  padding: var(--space-1) var(--space-3);
  font-size: 0.8rem;
}

.suggestion__add:disabled {
  border-color: var(--color-stone);
  color: var(--color-ink-soft);
  cursor: default;
}

.chat-status {
  font-size: 0.85rem;
  color: var(--color-ink-soft);
  margin: 0;
}

.chat-status--error {
  color: var(--color-danger);
}

.chat-form {
  display: flex;
  gap: var(--space-2);
}

.chat-form input {
  flex: 1;
  border: 1px solid var(--color-stone);
  background: var(--color-paper-raised);
  border-radius: 6px;
  padding: var(--space-2) var(--space-3);
  color: var(--color-ink);
  font-size: 0.85rem;
}

.chat-form button {
  border: none;
  background: var(--color-ink);
  color: var(--color-paper);
  border-radius: 6px;
  padding: var(--space-2) var(--space-4);
  font-size: 0.85rem;
  font-weight: 500;
}

.chat-form button:disabled {
  opacity: 0.5;
  cursor: default;
}
</style>
