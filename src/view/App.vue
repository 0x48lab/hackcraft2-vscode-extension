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
              :class="
                'inline-flex p-4 border-b-2 rounded-t-lg group ' +
                (tabName == 'connection'
                  ? 'text-blue-600 border-blue-600 active dark:text-blue-500 dark:border-blue-500'
                  : 'border-transparent hover:text-gray-600 hover:border-gray-300 dark:hover:text-gray-300')
              "
              :aria-current="tabName == 'connection' ? 'page' : undefined"
              @click="tabName = 'connection'"
            >
              <svg
                aria-hidden="true"
                :class="
                  'w-5 h-5 mr-0 ' +
                  (tabName == 'connection'
                    ? 'text-blue-600 dark:text-blue-500'
                    : 'text-gray-400 group-hover:text-gray-500 dark:text-gray-500 dark:group-hover:text-gray-300')
                "
                fill="currentColor"
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 96 960 960"
              >
                <path
                  fill-rule="evenodd"
                  fill="currentColor"
                  d="M450 776H280q-83 0-141.5-58.5T80 576q0-83 58.5-141.5T280 376h170v60H280q-58.333 0-99.167 40.765-40.833 40.764-40.833 99Q140 634 180.833 675q40.834 41 99.167 41h170v60ZM325 606v-60h310v60H325Zm185 170v-60h170q58.333 0 99.167-40.765 40.833-40.764 40.833-99Q820 518 779.167 477 738.333 436 680 436H510v-60h170q83 0 141.5 58.5T880 576q0 83-58.5 141.5T680 776H510Z"
                  clip-rule="evenodd"
                />
              </svg>
            </a>
          </li>
          <li class="mr-2">
            <a
              href="#"
              :class="
                'inline-flex p-4 border-b-2 rounded-t-lg group ' +
                (tabName == 'runner'
                  ? 'text-green-600 border-green-600 active dark:text-green-500 dark:border-green-500'
                  : 'border-transparent hover:text-gray-600 hover:border-gray-300 dark:hover:text-gray-300')
              "
              :aria-current="tabName == 'runner' ? 'page' : undefined"
              @click="tabName = 'runner'"
            >
              <svg
                aria-hidden="true"
                :class="
                  'w-5 h-5 mr-0 ' +
                  (tabName == 'runner'
                    ? 'text-green-600 dark:text-green-500'
                    : 'text-gray-400 group-hover:text-gray-500 dark:text-gray-500 dark:group-hover:text-gray-300')
                "
                fill="currentColor"
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 96 960 960"
              >
                <path
                  fill="currentColor"
                  d="m383 746 267-170-267-170v340Zm97 230q-82 0-155-31.5t-127.5-86Q143 804 111.5 731T80 576q0-83 31.5-156t86-127Q252 239 325 207.5T480 176q83 0 156 31.5T763 293q54 54 85.5 127T880 576q0 82-31.5 155T763 858.5q-54 54.5-127 86T480 976Z"
                ></path>
              </svg>
            </a>
          </li>
        </ul>
      </div>
    </div>

    <div v-if="tabName == 'connection'" class="w-full">
      <LabelInput
        id="serverAddress"
        v-model="settings.serverAddress"
        :label="t('serverAddress')"
        placeholder="e.g. localhost:25569"
      />
      <LabelSelect
        id="serverAddressHistory"
        v-model="settings.serverAddress"
        label=""
        :datalist="settings.serverAddressHistory"
      />
      <LabelInput
        id="playerId"
        v-model="settings.playerId"
        :label="t('playerId')"
        placeholder="e.g. hack-taro"
      />
      <LabelSelect
        id="playerIdHistory"
        v-model="settings.playerId"
        label=""
        :datalist="settings.playerIdHistory"
      />
    </div>
    <div class="w-full">
      <Hackcraft2Client
        ref="hackcraft2Client"
        :tabName="tabName"
        :server-address="settings.serverAddress"
        :player-id="settings.playerId"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import LabelInput from './components/LabelInput.vue'
import LabelSelect from './components/LabelSelect.vue'
import Hackcraft2Client from './components/Hackcraft2Client.vue'
const hackcraft2Client = ref()

const { t, locale } = useI18n()

const settings = reactive({
  serverAddress: '',
  playerId: '',
  serverAddressHistory: [],
  playerIdHistory: [],
  entities: [],
  entity: '',
})

const tabName = ref('connection')

const currentFile = ref('')
const lastFile = ref('')
const log = ref('')

onMounted(() => {
  loadConfig()
})

// Example of handling messages sent from the extension to the webview
window.addEventListener('message', (event) => {
  const message = event.data // The JSON data our extension sent
  console.log('message received: ' + message.command)

  switch (message.command) {
    case 'setCurrentFileExample':
      lastFile.value = currentFile.value
      currentFile.value = message.data
      return
    case 'loadConfig':
      onLoadConfig(event)
      return
    case 'saveConfig':
      onSaveConfig(event)
      return
    case 'getCurrentDocument':
      onGetCurrentDocument(event)
      return
  }
})

// Example of sending a message from the webview to the extension
const openLastFile = () => {
  vscode.postMessage({
    command: 'openFileExample',
    data: lastFile.value,
  })
}

const loadConfig = () => {
  vscode.postMessage({
    command: 'loadConfig',
    data: {},
  })
}

const onLoadConfig = (event: MessageEvent<any>) => {
  const message = event.data // The JSON data our extension sent
  console.log('onLoadConfig ' + message.command, message)
  log.value = JSON.stringify(message)
  const data = message.data

  settings.serverAddress = data.serverAddress
  settings.playerId = data.playerId
  settings.serverAddressHistory = data.serverAddressHistory
  settings.playerIdHistory = data.playerIdHistory
}

const saveConfig = () => {
  vscode.postMessage({
    command: 'saveConfig',
    data: {
      serverAddress: settings.serverAddress,
      playerId: settings.playerId,
    },
  })
}

const onSaveConfig = (event: MessageEvent<any>) => {
  const message = event.data // The JSON data our extension sent
  console.log('onSaveConfig ' + message.command, message)
  log.value = JSON.stringify(message)

  setTimeout(loadConfig, 500)
}

const onGetCurrentDocument = (event: MessageEvent<any>) => {
  const message = event.data // The JSON data our extension sent
  console.log('onGetCurrentDocument ' + message.command, message)

  hackcraft2Client.value.setDocument(message.data)
}
</script>
