<template>
  <div class="flex items-center mb-4">
    <div class="w-1/3">
      <label
        class="block text-gray-500 font-bold text-right mb-1 mb-0 pr-4"
        :for="props.id"
      >
        {{ props.label }}
      </label>
    </div>
    <div class="w-2/3">
      <input
        v-bind="$attrs"
        :id="props.id"
        v-model="text"
        class="bg-gray-200 appearance-none border-2 border-gray-200 rounded w-full py-2 px-4 text-gray-700 leading-tight focus:outline-none focus:border-purple-500"
        type="text"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, defineProps, withDefaults, defineEmits } from 'vue'

const props = withDefaults(
  defineProps<{
    id: string
    label: string
    modelValue: string
  }>(),
  {
    id: '',
    label: '',
    modelValue: '',
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', text: string): void
}>()

const text = computed({
  get: () => props.modelValue,
  set: (value) => {
    emit('update:modelValue', value)
  },
})
</script>
