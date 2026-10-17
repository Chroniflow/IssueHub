/// <reference types="vite/client" />

declare module 'vue' {
  export interface GlobalDirectives {
    vRipple: (typeof import('./src/directives/ripple'))['vRipple']
  }
}

declare global {
  interface ImportMetaEnv {
    readonly VITE_API_BASE_URL?: string
    readonly VITE_TURNSTILE_SITE_KEY?: string
  }

  interface ImportMeta {
    readonly env: ImportMetaEnv
  }

  interface Window {
    turnstile?: {
      render: (container: HTMLElement, options: Record<string, unknown>) => string
      reset: (widgetId: string) => void
      remove: (widgetId: string) => void
    }
  }
}

export {}
