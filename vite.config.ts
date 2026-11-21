import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig({
  plugins: [uni()],
  // Capacitor serves the build from https://localhost, so asset URLs must be root-absolute.
  base: '/'
})
