/// <reference types="vite/client" />

import type { S3HeadObjectSigner } from '@magrathea/object-storage-extension'

declare module '@magrathea/object-storage-extension/style.css'

declare global {
  interface Window {
    /** Optional in-memory deployment integration. It must not persist or log credentials. */
    __MAGRATHEA_S3_HEAD_SIGNER__?: S3HeadObjectSigner
  }
}
