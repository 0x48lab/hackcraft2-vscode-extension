<template>
  <div v-if="tabName == 'connection'" class="w-full mb-4">
    <div class="w-full">
      <div class="flex items-center mb-4">
        <div class="w-1/3"></div>
        <div class="w-2/3">
          <button
            v-if="entities.length == 0"
            class="text-white bg-blue-700 hover:bg-blue-800 focus:outline-none focus:ring-4 focus:ring-blue-300 font-medium rounded-full text-sm px-5 py-2.5 text-center mr-2 mb-2 dark:bg-blue-600 dark:hover:bg-blue-700 dark:focus:ring-blue-800 inline-flex items-center"
            @click="connect"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              height="1em"
              viewBox="0 96 960 960"
              width="1em"
              class="w-4 h-4 mr-2 -ml-1"
            >
              <path
                fill="currentColor"
                d="M450 776H280q-83 0-141.5-58.5T80 576q0-83 58.5-141.5T280 376h170v60H280q-58.333 0-99.167 40.765-40.833 40.764-40.833 99Q140 634 180.833 675q40.834 41 99.167 41h170v60ZM325 606v-60h310v60H325Zm185 170v-60h170q58.333 0 99.167-40.765 40.833-40.764 40.833-99Q820 518 779.167 477 738.333 436 680 436H510v-60h170q83 0 141.5 58.5T880 576q0 83-58.5 141.5T680 776H510Z"
              />
            </svg>
            {{ t('connect') }}
          </button>
          <button
            v-else
            class="text-white bg-purple-700 hover:bg-purple-800 focus:outline-none focus:ring-4 focus:ring-purple-300 font-medium rounded-full text-sm px-5 py-2.5 text-center mb-2 dark:bg-purple-600 dark:hover:bg-purple-700 dark:focus:ring-purple-900 inline-flex items-center"
            @click="disconnect"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              height="1em"
              viewBox="0 96 960 960"
              width="1em"
              class="w-4 h-4 mr-2 -ml-1"
            >
              <path
                fill="currentColor"
                d="m750 765-49-49q51-10 85-48t34-89q0-58-41-99t-99-41H525v-60h155q83 0 141.5 58.5T880 579q0 62-36 112t-94 74ZM594 609l-60-60h101v60h-41Zm220 391L63 249l43-43 751 751-43 43ZM450 776H280q-83 0-141.5-58.5T80 576q0-72 44.5-127T238 380l56 56h-14q-58 0-99 41t-41 99q0 58 41 99t99 41h170v60ZM325 606v-60h79l60 60H325Z"
              />
            </svg>
            {{ t('disconnect') }}
          </button>
        </div>
      </div>
    </div>
  </div>
  <div v-if="tabName == 'runner'" class="sm:flex sm:items-center mb-4">
    <div class="w-full">
      <div class="sm:flex sm:items-center mb-4">
        <div class="sm:w-1/3">
          <label
            class="block text-gray-500 font-bold sm:text-right mb-1 sm:mb-0 pr-4"
            for="entity"
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
            class="text-white bg-green-700 hover:bg-green-800 focus:outline-none focus:ring-4 focus:ring-green-300 font-medium rounded-full text-sm px-5 py-2.5 text-center mr-2 mb-2 dark:bg-green-600 dark:hover:bg-green-700 dark:focus:ring-green-800 inline-flex items-center"
            @click="runScript"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              height="1em"
              viewBox="0 96 960 960"
              width="1em"
              class="w-4 h-4 mr-2 -ml-1"
            >
              <path
                fill="currentColor"
                d="m383 746 267-170-267-170v340Zm97 230q-82 0-155-31.5t-127.5-86Q143 804 111.5 731T80 576q0-83 31.5-156t86-127Q252 239 325 207.5T480 176q83 0 156 31.5T763 293q54 54 85.5 127T880 576q0 82-31.5 155T763 858.5q-54 54.5-127 86T480 976Z"
              />
            </svg>
            {{ t('runScript') }}
          </button>

          <button
            v-else
            class="text-white bg-red-700 hover:bg-red-800 focus:outline-none focus:ring-4 focus:ring-red-300 font-medium rounded-full text-sm px-5 py-2.5 text-center mr-2 mb-2 dark:bg-red-600 dark:hover:bg-red-700 dark:focus:ring-red-800 inline-flex items-center"
            @click="stopScript"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              height="1em"
              viewBox="0 96 960 960"
              width="1em"
              class="w-4 h-4 mr-2 -ml-1"
            >
              <path
                fill="currentColor"
                d="M330 726h300V426H330v300Zm150 250q-82 0-155-31.5t-127.5-86Q143 804 111.5 731T80 576q0-83 31.5-156t86-127Q252 239 325 207.5T480 176q83 0 156 31.5T763 293q54 54 85.5 127T880 576q0 82-31.5 155T763 858.5q-54 54.5-127 86T480 976Z"
              />
            </svg>
            {{ t('stopScript') }}
          </button>
        </div>
      </div>
    </div>
  </div>

  <div class="sm:full">
    <p class="block text-gray-500 font-bold sm:text-right mb-1 sm:mb-0 pr-4">
      {{ t('status') }}
    </p>
    <div class="mb-4">{{ status }}</div>
    <p class="block text-gray-500 font-bold sm:text-right mb-1 sm:mb-0 pr-4">
      {{ t('log') }}
    </p>
    <textarea rows="10" v-model="log"> </textarea>
  </div>
</template>

<script setup lang="ts">
export interface Props {
  tabName?: string
}

import { defineProps, withDefaults, ref, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { useSettingStore } from '../../stores/settings'
import { useSourceFileStore } from '../../stores/sourceFiles'

const { t, locale } = useI18n()
const settingStore = useSettingStore()
const sourceFileStore = useSourceFileStore()

const props = withDefaults(defineProps<Props>(), {
  tabName: '',
})

const entities = ref<any[]>([])
const entityUuid = ref('')

const isRunning = ref(false)

const status = ref('')
const log = ref('')

const changeStatus = (message: any) => {
  console.log(message)
  status.value = message
}

const addLog = (message: any) => {
  console.log(message)
  log.value = log.value + '\n' + message
}

// --------------------------------------------
// WebSocket processes with 8x9craft server
// --------------------------------------------

let ws: WebSocket | null = null

const connect = () => {
  changeStatus('connecting...')
  addLog('connect')

  entities.value.splice(0, entities.value.length)
  isRunning.value = false

  if (ws != null) {
    ws.close()
  }

  ws = new WebSocket('ws://' + settingStore.setting.serverAddress + '/ws')

  ws.onopen = () => {
    changeStatus('Connected.')
    addLog('onOpen')

    // save config
    settingStore.save()

    // login
    const message = {
      type: 'login',
      data: {
        loginId: settingStore.setting.playerId,
      },
    }
    ws?.send(JSON.stringify(message))
  }

  ws.onclose = () => {
    changeStatus('Disconnected.')
    addLog('onClose')
    ws = null
    entities.value.splice(0, entities.value.length)
    isRunning.value = false
  }

  ws.onmessage = (event: MessageEvent) => {
    addLog('onMessage' + JSON.stringify(event))

    const json = JSON.parse(event.data.toString())
    addLog('websocket.onmessage data=' + event.data.toString())

    if (json.type === 'entities') {
      entities.value.splice(0, entities.value.length)
      const newEntities: [] = json.data
      if (newEntities.length > 0) {
        entities.value.push(...newEntities)
        const first: any = entities.value[0]
        entityUuid.value = first.entityUuid
      }
    } else if (json.type === 'status') {
      // atach(uuid) をコールするとStatusが返る
      //request
      //{
      //  "type": "attach",
      //      "data":{
      //          "entity": entity,
      //      }
      //  }
      //response
      //{
      //  "type": "status",
      //      "data":{
      //        "entityUuid": entityUuid, //実行中のスクリプトのエンティティUUID
      //        "isRunning": isRunning,   //実行中かどうか？
      //      }  
      //}
    } else if (json.type === 'message') {
      // nothing for message
    } else if (json.type === 'result') {
      changeStatus('Finished.' + '\n' + json.data)
      isRunning.value = false
    } else if (json.type === 'error') {
      changeStatus('Errored.' + '\n' + json.data)
      isRunning.value = false
    } else if (json.type === 'code') {
      // nothing for code
    }
  }

  ws.onerror = (event: Event) => {
    changeStatus('Exeption!!')
    addLog('onError')
    addLog(JSON.stringify(event))
  }
}

const disconnect = () => {
  changeStatus('Disconnecting...')
  addLog('disconnect')
  ws?.close()
}

const runScript = () => {
  changeStatus('Running...')
  addLog('runScript')

  isRunning.value = true

  sourceFileStore.getDocument()
}

sourceFileStore.$onAction(
  ({
    name, // 実行されたactionの名前
    store, // Storeのインスタンス。`myStore`に同じ
    args, // actionに渡された引数の配列
    after, // actionの完了後（Promiseであれば`resolve()`された後）に実行する処理
    onError, // actionで例外が投げられたとき（Promiseであれば`reject()`されたとき）に実行する処理
  }) => {
    // action実行前に行われる処理
    // console.log({
    //   name,
    //   store,
    //   args,
    // })

    after((result) => {
      // action実行後に行われる処理
      // console.log(result)

      // onGetCurrentDocument called from message dispather on App.vue
      if (name === 'onGetCurrentDocument') {
        addLog('sourceFileStore.$onAction#after onGetCurrentDocument')
        runScriptAfterSetText()
      }
    })

    onError((error) => {
      // actionでエラーが発生したときに行われる処理
      console.error(error)
    })
  }
)

const runScriptAfterSetText = () => {
  addLog('runScriptAfterSetText')
  const message = {
    type: 'run',
    data: {
      language: sourceFileStore.sourceFile.languageId,
      name: sourceFileStore.sourceFile.fileName,
      entity: entityUuid.value,
      code: sourceFileStore.sourceFile.code,
    },
  }
  ws?.send(JSON.stringify(message))
}

const stopScript = () => {
  changeStatus('Stopping...')
  addLog('stopScript')

  const message = {
    type: 'stop',
    data:{
            "entity": entityUuid.value,
        }
  }
  ws?.send(JSON.stringify(message))
}
</script>
