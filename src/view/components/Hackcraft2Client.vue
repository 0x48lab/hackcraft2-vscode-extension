<template>
  <div class="w-full mb-4">
    <div class="w-full">
      <div class="sm:flex sm:items-center mb-4">
        <div class="sm:w-1/3"></div>
        <div class="sm:w-2/3">
          <button
            v-if="entities.length == 0"
            class="shadow appearance-none border rounded"
            @click="connect"
          >
            {{ t('connect') }}
          </button>
          <button
            v-else
            class="shadow appearance-none border rounded"
            @click="disconnect"
          >
            {{ t('disconnect') }}
          </button>
        </div>
      </div>
    </div>
  </div>
  <div class="sm:flex sm:items-center mb-4">
    <div class="w-full">
      <div class="sm:flex sm:items-center mb-4">
        <div class="sm:w-1/3">
          <label
            class="block text-gray-500 font-bold sm:text-right mb-1 sm:mb-0 pr-4"
            :for="props.id"
          >
            {{ t('entity') }}
          </label>
        </div>
        <div v-if="entities.length == 0" class="sm:w-2/3">
          {{ t('pleaseConnect') }}
        </div>
        <div v-else class="sm:w-2/3">
          <select
            id="entity"
            v-model="entityUuid"
            :disabled="entities.length == 0"
            class="block appearance-none w-full text-black bg-white border border-gray-400 hover:border-gray-500 px-4 py-2 pr-8 rounded shadow leading-tight focus:outline-none focus:shadow-outline"
          >
            <option
              v-for="entity in entities"
              :key="entity.entityUuid"
              :value="entity.entityUuid"
            >
              {{ entity.name }}
            </option>
          </select>
        </div>
      </div>

      <div class="sm:flex sm:items-center mb-4">
        <div class="sm:w-1/3"></div>
        <div v-if="entities.length == 0" class="sm:w-2/3"></div>
        <div v-else class="sm:w-2/3">
          <button
            v-if="!isRunning"
            class="shadow appearance-none border rounded"
            @click="runScript"
          >
            {{ t('runScript') }}
          </button>
          <button
            v-else
            class="shadow appearance-none border rounded"
            @click="stopScript"
          >
            {{ t('stopScript') }}
          </button>
        </div>
      </div>
    </div>
  </div>

  <div class="sm:full">
    <textarea rows="10" v-model="status"> </textarea>
  </div>
</template>

<script setup lang="ts">
import { Hackcraf2SourceFile } from 'types/hackcraft2'
import { e } from 'vitest/dist/index-40ebba2b'
import { defineProps, withDefaults, ref, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
const { t, locale } = useI18n()

export interface Props {
  serverAddress?: string
  playerId?: string
}

const props = withDefaults(defineProps<Props>(), {
  serverAddress: '',
  playerId: '',
})

const entities = ref([])
const entityUuid = ref('')
const sourceFile: Hackcraf2SourceFile = reactive({
  fileName: '',
  languageId: '',
  code: '',
})

const isRunning = ref(false)

const status = ref('')

const log = (message: any) => {
  console.log(message)
  status.value = status.value + '\n' + message
}

let ws: WebSocket | null = null

const connect = () => {
  log('connect')
  entities.value.splice(0, entities.value.length)
  isRunning.value = false

  if (ws != null) {
    ws.close()
  }

  ws = new WebSocket('ws://' + props.serverAddress + '/ws')

  ws.onopen = () => {
    log('onOpen')

    // save config
    saveConfig()

    // login
    const message = {
      type: 'login',
      data: {
        loginId: props.playerId,
      },
    }
    ws?.send(JSON.stringify(message))
  }

  ws.onclose = () => {
    log('onClose')
    ws = null
    entities.value.splice(0, entities.value.length)
    isRunning.value = false
  }

  ws.onmessage = (event: MessageEvent) => {
    log('onMessage' + JSON.stringify(event))

    const json = JSON.parse(event.data.toString())
    log('websocket.onmessage data=' + event.data.toString())

    if (json.type === 'entities') {
      entities.value.splice(0, entities.value.length)
      const newEntities: [] = json.data
      if (newEntities.length > 0) {
        entities.value.push(...newEntities)
        const first: any = entities.value[0]
        entityUuid.value = first.entityUuid
      }
    } else if (json.type === 'message') {
      if (
        json.data == 'success' ||
        json.data == 'interrupted' ||
        /Exception/.test(json.data)
      ) {
        isRunning.value = false
      }
    } else if (json.type === 'code') {
      // nothing for code
    }
  }

  ws.onerror = (event: Event) => {
    log('onError')
    log(event)
  }
}

const saveConfig = () => {
  vscode.postMessage({
    command: 'saveConfig',
    data: {
      serverAddress: props.serverAddress,
      playerId: props.playerId,
    },
  })
}

const disconnect = () => {
  log('disconnect')
  ws?.close()
}

const getDocument = () => {
  log('getDocument')

  vscode.postMessage({
    command: 'getCurrentDocument',
    data: {},
  })
}

const runScript = () => {
  log('runScript')

  isRunning.value = true

  getDocument()
}

const setDocument = (data: any) => {
  log('setDocument' + JSON.stringify(data))

  Object.assign(sourceFile, data)

  runScriptAfterSetText()
}

const runScriptAfterSetText = () => {
  log('runScriptAfterSetText')
  const message = {
    type: 'run',
    data: {
      language: sourceFile.languageId,
      name: sourceFile.fileName,
      entity: entityUuid.value,
      code: sourceFile.code,
    },
  }
  ws?.send(JSON.stringify(message))
}

const stopScript = () => {
  log('stopScript')

  const message = {
    type: 'stop',
  }
  ws?.send(JSON.stringify(message))
}

defineExpose({
  // connect,
  // disconnect,
  // runScript,
  // stopScript,
  setDocument,
})
</script>
