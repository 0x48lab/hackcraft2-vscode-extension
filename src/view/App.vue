<template>
  <div class="flex items-start flex-col w-full mx-auto">
    <div class="flex items-center flex-col w-full mx-auto">
      <h1 class="text-lg">8x9craft2</h1>
    </div>

    <hr class="border-white w-full mt-4 mb-4" />

    <div class="w-full">
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
