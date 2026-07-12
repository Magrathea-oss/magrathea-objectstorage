import axe from 'axe-core'
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import {
  defaultProductIdentity,
  ProductShell,
  ShellBadge,
  ShellBanner,
  ShellCard,
  ShellDataTable,
  ShellDialog,
  ShellDisclosure,
  ShellField,
  ShellPageState,
  ShellTooltip,
} from '../src'

const navigation = Array.from({ length: 12 }, (_, index) => ({
  id: `section-${index + 1}`,
  labelKey: `Section ${index + 1}`,
  route: index === 0 ? '/' : `/section-${index + 1}`,
}))

function mountShowcase() {
  return mount({
    components: { ProductShell, ShellBadge, ShellBanner, ShellCard, ShellDataTable, ShellDialog, ShellField, ShellPageState },
    data: () => ({
      open: false,
      navigation,
      defaultProductIdentity,
      locales: [
        { locale: 'en', localizedName: 'English' },
        { locale: 'de', localizedName: 'Deutsch' },
        { locale: 'zh-CN', localizedName: '简体中文' },
      ],
    }),
    template: `
      <ProductShell :identity="defaultProductIdentity" :navigation="navigation" active-route="/" page-title="Overview"
        :locales="locales" selected-locale="en"
        :breadcrumbs="[{ label: 'Home', href: '/' }, { label: 'Overview' }]">
        <ShellBanner title="An update is available"><p>Review the latest changes.</p></ShellBanner>
        <ShellCard title="Example card"><ShellBadge label="Ready" tone="positive" /></ShellCard>
        <ShellDataTable caption="Recent work" :columns="[{ key: 'name', label: 'Name' }]" :rows="[{ id: '1', name: 'Planning' }]" />
        <ShellPageState state="empty" />
        <button type="button" class="shell-button" data-test="dialog-trigger" @click="open = true">Open dialog</button>
      </ProductShell>
      <ShellDialog id="example-dialog" v-model:open="open" title="Create item" description="Enter the details.">
        <ShellField id="item-name" label="Name"><template #default="field"><input :id="field.id"></template></ShellField>
      </ShellDialog>`,
  }, { attachTo: document.body })
}

describe('product-neutral shell components', () => {
  afterEach(() => { document.body.innerHTML = '' })

  it('has landmarks, current navigation, headings, and no detectable accessibility violations', async () => {
    const wrapper = mountShowcase()
    expect(wrapper.get('a[href="#main-content"]').text()).toContain('Skip')
    expect(wrapper.get('nav[aria-label="Primary navigation"]')).toBeTruthy()
    expect(wrapper.get('a[aria-current="page"]').text()).toBe('Section 1')
    expect(wrapper.get('main h1').text()).toBe('Overview')

    const result = await axe.run(document.body, { rules: { 'color-contrast': { enabled: false } } })
    expect(result.violations).toEqual([])
  })

  it.each(['loading', 'empty', 'error', 'offline', 'unavailable', 'unauthorized', 'not-found'] as const)(
    'renders /example/resources standard %s state with a named heading, announcement, and keyboard-ready recovery',
    async (state) => {
      window.history.replaceState({}, '', `/example/resources?state=${state}`)
      const focusSentinel = document.createElement('button')
      document.body.append(focusSentinel)
      focusSentinel.focus()
      const wrapper = mount({
        components: { ShellDataTable, ShellPageState },
        data: () => ({ state }),
        template: `
          <article aria-labelledby="resources-heading">
            <h1 id="resources-heading">Resources</h1>
            <ShellDataTable v-if="state === 'ready'" caption="Resources" :columns="[]" :rows="[]" />
            <ShellPageState v-else :state="state" />
          </article>`,
      }, { attachTo: document.body })

      expect(window.location.pathname).toBe('/example/resources')
      expect(document.activeElement).toBe(focusSentinel)
      const presentation = wrapper.get('.shell-page-state')
      const heading = presentation.get('h2')
      expect(heading.text().length).toBeGreaterThan(0)
      expect(presentation.attributes('aria-labelledby')).toBe(heading.attributes('id'))
      expect(presentation.attributes('aria-live')).toMatch(/polite|assertive/)
      expect(wrapper.text()).not.toMatch(/bucket|object storage|storage policy/i)

      const recoveryAction = {
        error: 'retry', offline: 'retry', unavailable: 'retry', unauthorized: 'sign-in', 'not-found': 'go-back',
      }[state]
      if (state === 'loading') expect(wrapper.get('[role="status"]').attributes('aria-label')).toContain('Loading')
      if (recoveryAction) {
        const button = wrapper.get('button')
        expect(button.attributes('type')).toBe('button')
        await button.trigger('click')
        expect(wrapper.getComponent(ShellPageState).emitted('recover')).toEqual([[recoveryAction]])
      } else {
        expect(wrapper.find('button').exists()).toBe(false)
      }
    },
  )

  it('keeps all twelve navigation contributions in logical DOM order with one programmatic current item', () => {
    const wrapper = mountShowcase()
    const links = wrapper.findAll('#primary-navigation a')
    expect(links).toHaveLength(12)
    expect(links.map((link) => link.text())).toEqual(navigation.map((entry) => entry.labelKey))
    expect(links.filter((link) => link.attributes('aria-current') === 'page')).toHaveLength(1)
  })

  it('exposes mobile navigation state, moves focus into the drawer, and restores it after Escape or backdrop dismissal', async () => {
    const wrapper = mountShowcase()
    const trigger = wrapper.get('button[aria-controls="primary-navigation"]')
    expect(trigger.attributes('aria-expanded')).toBe('false')
    expect(trigger.attributes('aria-label')).toBe('Open navigation')
    await trigger.trigger('click')
    expect(trigger.attributes('aria-expanded')).toBe('true')
    expect(trigger.attributes('aria-label')).toBe('Close navigation')
    expect(wrapper.find('.product-shell__backdrop').exists()).toBe(true)
    expect(document.activeElement).toBe(wrapper.get('#primary-navigation a').element)

    await wrapper.get('#primary-navigation').trigger('keydown', { key: 'Escape' })
    await nextTick()
    expect(trigger.attributes('aria-expanded')).toBe('false')
    expect(document.activeElement).toBe(trigger.element)

    await trigger.trigger('click')
    await wrapper.get('.product-shell__backdrop').trigger('click')
    await nextTick()
    expect(trigger.attributes('aria-expanded')).toBe('false')
    expect(document.activeElement).toBe(trigger.element)
  })

  it('renders task-grouped navigation with semantic SVG icons and named status text while retaining legacy entries', () => {
    const wrapper = mount(ProductShell, {
      props: {
        pageTitle: 'Operations', activeRoute: '/health',
        navigationGroups: [{ id: 'assess', labelKey: 'Assess service health', icon: 'gauge', order: 1 }],
        navigation: [
          { id: 'legacy', labelKey: 'Legacy destination', route: '/legacy', order: 3 },
          { id: 'health', labelKey: 'Service health', route: '/health', groupId: 'assess', icon: 'activity', status: { labelKey: 'Needs attention', tone: 'warning' }, order: 1 },
        ],
      },
      attachTo: document.body,
    })

    expect(wrapper.get('.product-shell__nav-section-heading h2').text()).toBe('Assess service health')
    expect(wrapper.findAll('#primary-navigation a').map((link) => link.text())).toEqual(['Service healthNeeds attention', 'Legacy destination'])
    expect(wrapper.get('a[aria-current="page"] .product-shell__nav-status').text()).toBe('Needs attention')
    expect(wrapper.findAll('#primary-navigation svg').length).toBeGreaterThanOrEqual(3)
  })

  it('offers controlled light, dark, and system appearance without owning persistence', async () => {
    const wrapper = mount(ProductShell, {
      props: { pageTitle: 'Overview', appearance: 'dark', showAppearanceControl: true },
      attachTo: document.body,
    })
    expect(wrapper.get('.product-shell').attributes('data-appearance')).toBe('dark')
    const selector = wrapper.get('select[aria-label="Appearance"]')
    expect(selector.findAll('option').map((option) => option.attributes('value'))).toEqual(['system', 'light', 'dark'])
    await selector.setValue('light')
    expect(wrapper.emitted('appearanceChange')).toEqual([['light']])
    expect(wrapper.get('.product-shell').attributes('data-appearance')).toBe('dark')
  })

  it('provides keyboard-native disclosures and opt-in accessible tooltip descriptions', async () => {
    const wrapper = mount({
      components: { ShellDisclosure, ShellTooltip },
      template: `<ShellDisclosure title="Diagnostic detail" summary="Supporting evidence"><p>Technical context</p></ShellDisclosure>
        <ShellTooltip text="Explains the adjacent value"><span aria-label="More information">Info</span></ShellTooltip>`,
    }, { attachTo: document.body })
    const disclosure = wrapper.get('details')
    expect(disclosure.attributes('open')).toBeUndefined()
    await disclosure.get('summary').trigger('click')
    expect((disclosure.element as HTMLDetailsElement).open).toBe(true)
    const tooltip = wrapper.get('[role="tooltip"]')
    expect(wrapper.get('.shell-tooltip__trigger').attributes('aria-describedby')).toBe(tooltip.attributes('id'))
    expect(tooltip.text()).toBe('Explains the adjacent value')

    const result = await axe.run(wrapper.element, { rules: { 'color-contrast': { enabled: false } } })
    expect(result.violations).toEqual([])
  })

  it('preserves the focused locale control and complete focus order while localized names change', async () => {
    const wrapper = mount({
      components: { ProductShell },
      data: () => ({
        locale: 'en', navigation, defaultProductIdentity,
        locales: [
          { locale: 'en', localizedName: 'English' },
          { locale: 'de', localizedName: 'Deutsch' },
          { locale: 'zh-CN', localizedName: '简体中文' },
        ],
      }),
      computed: {
        localizedLabels() {
          return this.locale === 'de'
            ? { locale: 'Sprache', primaryNavigation: 'Hauptnavigation', openNavigation: 'Navigation öffnen' }
            : { locale: 'Language', primaryNavigation: 'Primary navigation', openNavigation: 'Open navigation' }
        },
      },
      template: `<ProductShell :identity="defaultProductIdentity" :navigation="navigation" active-route="/" page-title="Overview"
        :locales="locales" :selected-locale="locale" :labels="localizedLabels" @locale-change="locale = $event" />`,
    }, { attachTo: document.body })
    const focusOrder = () => Array.from(wrapper.element.querySelectorAll<HTMLElement>('a[href], button, select'))
      .map((element) => `${element.tagName}:${element.getAttribute('href') || element.getAttribute('aria-controls') || element.className}`)
    const before = focusOrder()
    const selector = wrapper.get('select[aria-label="Language"]')
    expect(selector.findAll('option').map((option) => option.text())).toEqual(['English', 'Deutsch', '简体中文'])
    expect(selector.findAll('option')[2].attributes('lang')).toBe('zh-CN')
    ;(selector.element as HTMLSelectElement).focus()

    await selector.setValue('de')
    await nextTick()
    expect(document.activeElement).toBe(selector.element)
    expect(wrapper.get('select[aria-label="Sprache"]').element).toBe(selector.element)
    expect(focusOrder()).toEqual(before)
    expect(wrapper.findAll('.product-shell__header')).toHaveLength(1)
    expect(wrapper.findAll('nav[aria-label="Hauptnavigation"]')).toHaveLength(1)
    expect(wrapper.findAll('main')).toHaveLength(1)
    expect(wrapper.get('#primary-navigation a[aria-current="page"]').text()).toBe('Section 1')
    expect(wrapper.findAll('#primary-navigation a').map((link) => link.text())).toEqual(navigation.map((entry) => entry.labelKey))
  })

  it('renders localized extension failures from the safe contract and emits retry', async () => {
    const wrapper = mount(ProductShell, {
      props: {
        pageTitle: 'Overview',
        extensionLoadStates: [{
          extensionId: 'example', status: 'error', messageKey: 'shell.extensions.loadError',
          messageParameters: { extension: 'Example' }, recoveryAction: 'retry',
        }],
        labels: {
          extensionLoadError: (name: string) => `${name} konnte nicht geladen werden.`,
          retry: 'Erneut versuchen',
        },
      },
      attachTo: document.body,
    })
    const alert = wrapper.get('[role="alert"]')
    expect(alert.text()).toContain('Example konnte nicht geladen werden.')
    expect(alert.text()).not.toMatch(/stack|exception|technical/i)
    await alert.get('button').trigger('click')
    expect(wrapper.emitted('retryExtension')).toEqual([['example']])

    const result = await axe.run(wrapper.element, { rules: { 'color-contrast': { enabled: false } } })
    expect(result.violations).toEqual([])
  })

  it('applies documented brand slots and falls back to an accessible Magrathea mark', async () => {
    const wrapper = mount(ProductShell, {
      props: { pageTitle: 'Overview', brandTokens: { '--shell-brand': '#123456' } },
      attachTo: document.body,
    })
    expect(wrapper.get('.product-shell').attributes('style')).toContain('--shell-brand: #123456')
    expect(wrapper.get('.product-shell__identity').attributes('aria-label')).toBe('Magrathea home')
    expect(wrapper.get('.product-shell__mark').text()).toBe('M')
    expect(wrapper.get('.product-shell__mark').attributes('aria-hidden')).toBe('true')

    await wrapper.setProps({ identity: { name: 'Example', accessibleName: 'Example product', logoUrl: '/missing.svg' } })
    await wrapper.get('img').trigger('error')
    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.get('.product-shell__mark').text()).toBe('M')
    expect(wrapper.get('.product-shell__identity').attributes('aria-label')).toBe('Example product home')
  })

  it('labels a modal, traps focus, closes on Escape, and restores the trigger', async () => {
    const wrapper = mountShowcase()
    const trigger = wrapper.get('[data-test="dialog-trigger"]')
    ;(trigger.element as HTMLElement).focus()
    await trigger.trigger('click')
    await nextTick()

    const dialog = document.querySelector<HTMLElement>('[role="dialog"]')
    expect(dialog?.getAttribute('aria-modal')).toBe('true')
    expect(dialog?.getAttribute('aria-labelledby')).toBe('example-dialog-title')
    expect(document.activeElement?.getAttribute('aria-label')).toBe('Close dialog')

    dialog?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))
    await nextTick()
    expect(document.querySelector('[role="dialog"]')).toBeNull()
    expect(document.activeElement).toBe(trigger.element)
    wrapper.unmount()
  })
})
