<script setup lang="ts">
defineProps<{
  title: string
  description: string
  items?: Array<{ label: string; detail: string }>
}>()
</script>

<template>
  <section class="page-guide">
    <div class="page-guide__copy">
      <h2>{{ title }}</h2>
      <p>{{ description }}</p>
    </div>
    <div v-if="items?.length" class="page-guide__items">
      <div v-for="item in items" :key="item.label" class="page-guide__item">
        <strong>{{ item.label }}</strong>
        <span>{{ item.detail }}</span>
      </div>
    </div>
  </section>
</template>

<style scoped>
.page-guide {
  display: flex;
  align-items: stretch;
  justify-content: space-between;
  gap: 20px;
  padding: 18px 20px;
  margin-bottom: 16px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-surface);
}

.page-guide__copy {
  min-width: 260px;
}

.page-guide h2 {
  margin: 0;
  color: var(--app-text-strong);
  font-size: 18px;
  font-weight: 700;
}

.page-guide p {
  margin: 8px 0 0;
  max-width: 680px;
  color: var(--app-text-secondary);
  font-size: 13px;
  line-height: 1.65;
}

.page-guide__items {
  display: grid;
  grid-template-columns: repeat(3, minmax(150px, 1fr));
  gap: 10px;
  min-width: 420px;
}

.page-guide__item {
  padding: 10px 12px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  background: var(--app-surface-soft);
}

.page-guide__item strong {
  display: block;
  color: var(--app-text);
  font-size: 13px;
}

.page-guide__item span {
  display: block;
  margin-top: 4px;
  color: var(--app-text-secondary);
  font-size: 12px;
  line-height: 1.45;
}

@media (max-width: 960px) {
  .page-guide {
    flex-direction: column;
  }

  .page-guide__items {
    grid-template-columns: 1fr;
    min-width: 0;
  }
}
</style>
