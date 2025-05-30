<template>
  <div class="flex items-start flex-col w-full mx-auto">
    <div class="flex items-center flex-col w-full mx-auto">
      <h1 class="text-lg">8x9craft2</h1>
    </div>
    <div class="w-full mb-4">
      <div class="border-b border-gray-200 dark:border-gray-700">
        <ul
          class="flex flex-wrap -mb-px text-sm font-medium text-center text-gray-500 dark:text-gray-400"
        >
          <li class="mr-2">
            <a
              href="#"
              class="inline-flex p-4 border-b-2 rounded-t-lg group text-blue-600 border-blue-600 active dark:text-blue-500 dark:border-blue-500"
              aria-current="page"
            >
              {{ t('log') }}
            </a>
          </li>
        </ul>
      </div>
    </div>
    <div class="w-full">
      <div class="sm:full">
        <p class="block text-gray-500 font-bold sm:text-right mb-1 sm:mb-0 pr-4">
          {{ t('status') }}
        </p>
        <div
          class="mb-4 text-white"
          style="white-space: pre-wrap"
          v-text="status"
        ></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useLog } from '../composables/consoleLog'
import { useSourceFileStore } from '../stores/sourceFiles'

const { t, locale } = useI18n()
const sourceFileStore = useSourceFileStore()

const status = ref('')

onMounted(() => {
  useLog().info('8x9craft2 extension mounted')
})

// handling messages sent from the extension to the webview
window.addEventListener('message', (event) => {
  const message = event.data // The JSON data our extension sent
  console.log('message received: ' + message.command)

  switch (message.command) {
    case 'setCurrentFileExample':
      return
    case 'onGetCurrentDocument':
      sourceFileStore.onGetCurrentDocument(event)
      return
  }
})
</script>
