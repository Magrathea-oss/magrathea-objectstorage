import axe from 'axe-core'
import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import App from '../src/App.vue'

describe('product application template accessibility', () => {
  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('renders its placeholder without detectable accessibility violations', async () => {
    const wrapper = mount(App, { attachTo: document.body })
    const result = await axe.run(wrapper.element, {
      rules: { 'color-contrast': { enabled: false } },
    })

    expect(result.violations).toEqual([])
  })
})
