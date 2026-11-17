import { useUploadsStore } from '@/stores/uploads'
import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import './assets/main.css'

import App from './App.vue'
import router from './router'
import { pinia } from './stores'
import { useAuthStore } from './stores/auth'
import { setUnauthorizedHandler } from './utils/request'

const app = createApp(App)

app.use(pinia)
app.use(ElementPlus, { locale: zhCn })

setUnauthorizedHandler(() => {
  const auth = useAuthStore(pinia)
  useUploadsStore(pinia).reset()
  auth.clearSession()

  const currentRoute = router.currentRoute.value

  if (currentRoute.name !== 'login') {
    router.replace({
      name: 'login',
      query: {
        redirect: currentRoute.fullPath,
        reason: 'expired',
      },
    })
  }
})

app.use(router)
app.mount('#app')