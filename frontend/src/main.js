import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import './style.css'

const app = createApp(App)

// 全量注册图标：教学项目不折腾按需引入。
// 代价是打包体积大一点（~几百 KB），收益是模板里 <el-icon><Plus /></el-icon> 随便用
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
// zh-cn：Element Plus 组件内置文案（分页、日期选择器、确认框按钮）显示中文
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
