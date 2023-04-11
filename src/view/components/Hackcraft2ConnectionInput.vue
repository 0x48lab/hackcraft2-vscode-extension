<template>
  <div v-if="props.tabName == 'connection'" class="w-full">
    <LabelInput
      id="serverAddress"
      v-model="settingStore.setting.serverAddress"
      :label="t('serverAddress')"
      placeholder="e.g. localhost:25569"
    />
    <LabelSelect
      id="serverAddressHistory"
      v-model="settingStore.setting.serverAddress"
      label=""
      :datalist="settingStore.setting.serverAddressHistory"
    />
    <LabelInput
      id="playerId"
      v-model="settingStore.setting.playerId"
      :label="t('playerId')"
      placeholder="e.g. hack-taro"
    />
    <LabelSelect
      id="playerIdHistory"
      v-model="settingStore.setting.playerId"
      label=""
      :datalist="settingStore.setting.playerIdHistory"
    />
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import LabelInput from './atoms/LabelInput.vue'
import LabelSelect from './atoms/LabelSelect.vue'

import { useSettingStore } from '../../stores/settings'

export interface Props {
  tabName?: string
}

const settingStore = useSettingStore()

const { t, locale } = useI18n()

const props = withDefaults(defineProps<Props>(), {
  tabName: '',
})
</script>
