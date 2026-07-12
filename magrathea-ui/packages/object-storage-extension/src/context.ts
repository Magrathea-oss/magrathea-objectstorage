import type { InjectionKey } from 'vue'
import type { AdminApiClient } from './adapters/admin-api-client'
import type { S3HeadObjectClient } from './adapters/s3-head-object-client'

export const adminApiClientKey: InjectionKey<AdminApiClient> = Symbol('object-storage-admin-api')
export const s3HeadObjectClientKey: InjectionKey<S3HeadObjectClient | undefined> = Symbol('object-storage-s3-head-object')
