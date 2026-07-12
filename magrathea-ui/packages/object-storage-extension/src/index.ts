export { default as AdminPage } from './AdminPage.vue'
export { adminApiClientKey, s3HeadObjectClientKey } from './context'
export { createObjectStorageExtension, objectStorageEnglishMessages } from './extension'

export {
  AdminClientError,
  createAdminApiClient,
} from './adapters/admin-api-client'
export type {
  AdminApiClient,
  AdminApiClientOptions,
  AdminClientErrorKind,
  AdminErrorDocument,
  AdminHealth,
  AdminLiveness,
  AdminReadiness,
  BackendStatus,
  BucketCapacity,
  CatalogStatus,
  DiskSet,
  DiskSetCollection,
  OperationalReport,
  OperationalReportType,
  PolicyValidationError,
  PolicyValidationResult,
  ReadinessComponent,
  StorageDevice,
  StorageDeviceCollection,
  StoragePolicy,
  StoragePolicyCollection,
  StoragePolicyProposal,
} from './adapters/admin-api-client'
export {
  createS3HeadObjectClient,
  S3DiagnosticError,
} from './adapters/s3-head-object-client'
export type {
  S3DiagnosticErrorKind,
  S3HeadObjectClient,
  S3HeadObjectClientOptions,
  S3HeadObjectInput,
  S3HeadObjectResult,
  S3HeadObjectSigner,
} from './adapters/s3-head-object-client'
