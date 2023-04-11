import { createApp } from 'vue'
import { createI18n } from 'vue-i18n'
import App from './App.vue'
import { messages } from '../locales'
import { createPinia } from "pinia";

const i18n = createI18n({
  legacy: false,
  locale: 'en',
  messages,
})

const pinia = createPinia()

const app = createApp(App)
app.use(i18n)
app.use(pinia)
app.mount('#app')
