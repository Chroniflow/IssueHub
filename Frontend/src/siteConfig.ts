import { ref } from 'vue'
import { fetchSiteConfig } from './api/http'

/** Bundled image keeps the header and browser tab branded while the API is unavailable. */
export const DEFAULT_SITE_ICON = '/favicon.svg'
export const siteIconUrl = ref(DEFAULT_SITE_ICON)

function normalizedIconUrl(value: string | null | undefined) {
  const candidate = value?.trim() ?? ''
  return candidate || DEFAULT_SITE_ICON
}

export function applySiteIcon(value: string | null | undefined) {
  const url = normalizedIconUrl(value)
  siteIconUrl.value = url
  const links = document.querySelectorAll<HTMLLinkElement>('link[data-site-icon]')
  links.forEach((link) => {
    link.href = url
    if (url === DEFAULT_SITE_ICON) link.type = 'image/svg+xml'
    else link.removeAttribute('type')
  })
}

export async function loadSiteConfig() {
  try {
    const config = await fetchSiteConfig()
    applySiteIcon(config.iconUrl)
  } catch {
    // The bundled icon is intentionally retained for offline/static frontend use.
  }
}
