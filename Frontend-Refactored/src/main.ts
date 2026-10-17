import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'
import { vRipple } from './directives/ripple'

const app = createApp(App)

app.use(createPinia())
app.use(router)

app.directive('ripple', vRipple)

app.mount('#app')
