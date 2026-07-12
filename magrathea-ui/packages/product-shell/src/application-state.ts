import type { ExtensionLoadState, ProductComposition } from './composition'

export type StandardPageState =
  | 'loading'
  | 'ready'
  | 'empty'
  | 'error'
  | 'offline'
  | 'unavailable'
  | 'unauthorized'
  | 'not-found'

export type RecoveryAction = 'retry' | 'go-back' | 'sign-in' | 'none'

export interface PageStatePresentation {
  readonly state: Exclude<StandardPageState, 'ready'>
  readonly headingKey: string
  readonly messageKey: string
  readonly announcement: 'polite' | 'assertive'
  readonly recoveryAction: RecoveryAction
}

export type ResourceState<T, E = unknown> =
  | { readonly status: 'loading'; readonly previous?: T }
  | { readonly status: 'ready'; readonly data: T }
  | { readonly status: 'empty' }
  | { readonly status: 'error'; readonly error: E; readonly previous?: T }
  | { readonly status: 'offline'; readonly previous?: T }
  | { readonly status: 'unavailable'; readonly reason?: string }
  | { readonly status: 'unauthorized' }
  | { readonly status: 'not-found' }

export interface ApplicationState {
  readonly locale: string
  readonly navigationExpanded: boolean
  readonly activeRoute?: string
  readonly extensionLoadStates: readonly ExtensionLoadState[]
}

export interface ApplicationStateStore {
  get(): Readonly<ApplicationState>
  update(update: (current: Readonly<ApplicationState>) => ApplicationState): Readonly<ApplicationState>
  subscribe(listener: (state: Readonly<ApplicationState>) => void): () => void
}

export const standardPagePresentations: Readonly<
  Record<Exclude<StandardPageState, 'ready'>, PageStatePresentation>
> = Object.freeze({
  loading: presentation('loading', 'polite', 'none'),
  empty: presentation('empty', 'polite', 'none'),
  error: presentation('error', 'assertive', 'retry'),
  offline: presentation('offline', 'assertive', 'retry'),
  unavailable: presentation('unavailable', 'assertive', 'retry'),
  unauthorized: presentation('unauthorized', 'assertive', 'sign-in'),
  'not-found': presentation('not-found', 'polite', 'go-back'),
})

function presentation(
  state: Exclude<StandardPageState, 'ready'>,
  announcement: 'polite' | 'assertive',
  recoveryAction: RecoveryAction,
): PageStatePresentation {
  return Object.freeze({
    state,
    headingKey: `shell.states.${state}.heading`,
    messageKey: `shell.states.${state}.message`,
    announcement,
    recoveryAction,
  })
}

export function createApplicationStateStore(initial?: Partial<ApplicationState>): ApplicationStateStore {
  let state = freezeState({
    locale: 'en',
    navigationExpanded: false,
    extensionLoadStates: [],
    ...initial,
  })
  const listeners = new Set<(state: Readonly<ApplicationState>) => void>()

  return {
    get: () => state,
    update(update) {
      state = freezeState(update(state))
      listeners.forEach((listener) => listener(state))
      return state
    },
    subscribe(listener) {
      listeners.add(listener)
      return () => listeners.delete(listener)
    },
  }
}

export function withProductComposition(
  state: Readonly<ApplicationState>,
  composition: Pick<ProductComposition, 'extensionLoadStates'>,
): ApplicationState {
  return {
    ...state,
    extensionLoadStates: composition.extensionLoadStates,
  }
}

function freezeState(state: ApplicationState): Readonly<ApplicationState> {
  return Object.freeze({
    ...state,
    extensionLoadStates: Object.freeze([...state.extensionLoadStates]),
  })
}
