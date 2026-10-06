import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const mainEntryUrl = new URL('./main.ts', import.meta.url)

describe('application entry icon loading', () => {
  it('does not globally register the Element Plus icon catalogue', async () => {
    const mainEntry = await readFile(fileURLToPath(mainEntryUrl), 'utf8')

    expect(mainEntry).not.toContain("./components/icons/global-icons")
    expect(mainEntry).not.toContain('app.component(')
  })
})
