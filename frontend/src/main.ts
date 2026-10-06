import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import 'virtual:uno.css'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import App from './App.vue'
import pinia from './store'
import router from './router'
import './assets/styles/global.css'

const app = createApp(App)

app.use(pinia)
app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
