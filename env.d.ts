/// <reference types="vite/client" />

/**
 * 环境变量类型声明
 * 声明后，import.meta.env.VITE_XXX 才有类型提示和自动补全。
 */
interface ImportMetaEnv {
  /** 站点标题 */
  readonly VITE_APP_TITLE: string
  /** 接口基础路径，例如 /api */
  readonly VITE_API_BASE_URL: string
  /** 是否启用 Mock：字符串 "true" / "false" */
  readonly VITE_USE_MOCK: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
