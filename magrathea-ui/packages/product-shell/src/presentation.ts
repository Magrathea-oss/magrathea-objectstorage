export interface ShellBreadcrumb {
  readonly label: string
  readonly href?: string
}

/** Product-neutral shell copy. Applications may replace these values with localized text. */
export interface ProductShellLabels {
  readonly skipToMain: string
  readonly primaryNavigation: string
  readonly openNavigation: string
  readonly closeNavigation: string
  readonly menu: string
  readonly explore: string
  readonly workspace: string
  readonly locale: string
  readonly retry: string
  readonly extensionLoadError: (extensionName: string) => string
}

export interface ShellTableColumn {
  readonly key: string
  readonly label: string
  readonly align?: 'start' | 'center' | 'end'
}
