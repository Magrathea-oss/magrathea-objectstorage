import type {
  BrandTokenOverrides,
  BrandTokenSlot,
  ProductIdentity,
  ProductShellConfiguration,
} from './contracts'

export const defaultProductIdentity: Readonly<ProductIdentity> = Object.freeze({
  name: 'Magrathea',
  accessibleName: 'Magrathea',
  mark: 'M',
})

export const brandTokenSlots: readonly BrandTokenSlot[] = Object.freeze([
  '--shell-brand-strong',
  '--shell-brand',
  '--shell-brand-accent',
  '--shell-brand-soft',
])

export const defaultBrandTokens: Readonly<Record<BrandTokenSlot, string>> = Object.freeze({
  '--shell-brand-strong': '#173b3f',
  '--shell-brand': '#0f6564',
  '--shell-brand-accent': '#d17a22',
  '--shell-brand-soft': '#dcefed',
})

export function resolveProductIdentity(
  configuration: Pick<ProductShellConfiguration, 'identity' | 'brand'> = {},
): Readonly<ProductIdentity> {
  const override = configuration.identity ?? configuration.brand
  const name = override?.name?.trim() || defaultProductIdentity.name
  const logoUrl = override?.logoUrl?.trim()
  return Object.freeze({
    name,
    accessibleName: override?.accessibleName?.trim() || name,
    mark: override?.mark?.trim() || defaultProductIdentity.mark,
    ...(logoUrl ? { logoUrl } : {}),
  })
}

export function resolveBrandTokens(overrides: BrandTokenOverrides = {}): Readonly<Record<BrandTokenSlot, string>> {
  return Object.freeze(Object.fromEntries(
    brandTokenSlots.map((slot) => [slot, overrides[slot]?.trim() || defaultBrandTokens[slot]]),
  ) as Record<BrandTokenSlot, string>)
}
