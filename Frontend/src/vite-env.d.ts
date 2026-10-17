/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string
  readonly VITE_TURNSTILE_SITE_KEY?: string
}

interface Window {
  turnstile?: {
    render: (container: HTMLElement, options: Record<string, unknown>) => string
    reset: (widgetId: string) => void
    remove: (widgetId: string) => void
  }
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
