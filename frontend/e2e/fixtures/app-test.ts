import { expect, test as base } from '@playwright/test'

type BrowserDiagnostics = {
  browserDiagnostics: void
}

export const test = base.extend<BrowserDiagnostics>({
  browserDiagnostics: [
    async ({ context, page }, use, testInfo) => {
      const diagnostics: string[] = []
      const pageErrors: string[] = []

      const captureDiagnostics = (browserPage: typeof page) => {
        browserPage.on('pageerror', (error) => {
          const diagnostic = `pageerror: ${error.name}: ${error.message}`
          pageErrors.push(diagnostic)
          diagnostics.push(diagnostic)
        })
        browserPage.on('console', (message) => {
          if (message.type() === 'error') {
            diagnostics.push('console: uma mensagem de erro foi emitida')
          }
        })
        browserPage.on('response', (response) => {
          if (response.status() < 400) return

          const url = new URL(response.url())
          diagnostics.push(
            `http: ${response.request().method()} ${url.origin}${url.pathname} -> ${response.status()}`,
          )
        })
      }

      captureDiagnostics(page)
      context.on('page', captureDiagnostics)

      await use()

      const pageErrorWillFailTest =
        pageErrors.length > 0 && testInfo.status === testInfo.expectedStatus
      if (
        diagnostics.length > 0 &&
        (testInfo.status !== testInfo.expectedStatus || pageErrorWillFailTest)
      ) {
        await testInfo.attach('browser-diagnostics', {
          body: diagnostics.join('\n'),
          contentType: 'text/plain',
        })
      }

      if (testInfo.status === testInfo.expectedStatus) {
        expect(
          pageErrors,
          'Erros JavaScript não tratados no navegador',
        ).toEqual([])
      }
    },
    { auto: true },
  ],
})

export { expect }
