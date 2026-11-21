import { registerPlugin } from '@capacitor/core'

export interface IncomingFile {
  uri: string
  path: string
  name: string
  size: number
  mime: string
}

export interface OpenNative {
  takePending(): Promise<Partial<IncomingFile>>
  install(o: { path: string }): Promise<void>
}

export const Open = registerPlugin<OpenNative>('Open')
