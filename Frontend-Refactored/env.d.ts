/// <reference types="vite/client" />

declare module 'vue' {
  export interface GlobalDirectives {
    vRipple: (typeof import('./src/directives/ripple'))['vRipple']
  }
}

export {}
