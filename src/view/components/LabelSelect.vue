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
      <select
        v-bind="$attrs"
        :id="props.id"
        v-model="text"
        class="block appearance-none w-full text-black bg-white border border-gray-400 hover:border-gray-500 px-4 py-2 pr-8 rounded shadow leading-tight focus:outline-none focus:shadow-outline"
      >
        <option v-for="data in props.datalist" :key="data" :value="data">
          {{ data }}
        </option>
      </select>
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
    datalist: string[]
  }>(),
  {
    id: '',
    label: '',
    modelValue: '',
    datalist: undefined,
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
