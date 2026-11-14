/**
 * 应用入口
 * 职责：按顺序装配 Pinia（状态管理）→ Router（路由）→ Element Plus（UI 库）
 */
import { createApp } from 'vue'
import { createPinia } from 'pinia'

// Element Plus 采用完整引入：课程项目以「能用、好懂」为先，
// 完整引入不需要配置 unplugin 自动按需导入，代价是打包体积略大。
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'
import './styles/index.css'

const app = createApp(App)

// 全局注册 Element Plus 的所有图标，模板里可直接 <el-icon><Search /></el-icon>
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
