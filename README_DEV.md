# 8x9craft2 Extension

<p align="center">
  <img src="./Screenshot.png" width="350" alt="Screenshot of 8x9craft2">
</p>

8x9craft2 extension build with [Base Vue 3 Sidebar View Extension](https://github.com/joesobo/Vue3BaseExtension)
A Vue 3, Vite built template can be used to create a VSCode sidebar extension by providing a structure code and utilizing the vite build tool to compile and bundle the extension for deployment to VSCode. This template contains everything you need to get started building your extension right away. With this template, you can leverage the features and capabilities of Vue 3 to build powerful and interactive extensions for VSCode.

## Includes:

- [x] Typescript
- [x] Vue 3
- [x] Vite
- [x] Tailwindcss
  - [x] Vscode Default style Tailwind config
- [x] Iconify via unplugin
- [x] I18n via unplugin
- [x] Vitest
  - [x] Vitest UI
- [x] Testing Library
- [x] Decent Linting
- [x] CircleCI
- [x] Basic VSCode API Message Examples

## Get Started:

Install dependencies:

- `yarn install`

Run the extension:

- Enter `yarn watch` in the console
- Press `f5` on the `extension.ts` to open a debug window (or select in menu "Debug" -> "Run Extension")
- Navigate to the extension icon seen on the left sidebar (or open command palette (`Ctrl/Cmd + Shift + P`) and select `View: 8x9craft2` to open webview view.)

## Development Workflow

1. Make your changes
2. Run tests: `yarn test` or `yarn test:ui` for visual test runner
3. Lint and format: `yarn fix`
4. Type check: `yarn typecheck`
5. Build: `yarn compile`

## Publishing

### Manual Publishing

1. Install vsce globally: `npm install -g @vscode/vsce`
2. Package the extension: `yarn package`
3. Publish to marketplace: `vsce publish`

### Automated Publishing with GitHub Actions

The extension is automatically published to the VSCode Marketplace when changes are pushed to the main branch. This is handled by GitHub Actions.

To set up automated publishing:

1. Get a Personal Access Token (PAT) from Azure DevOps:
   - Go to [Azure DevOps](https://dev.azure.com/)
   - Click your profile icon → Personal access tokens
   - Create new token with Marketplace (Manage) scope
   - Copy the generated token

2. Add the token to GitHub repository secrets:
   - Go to repository Settings → Secrets and variables → Actions
   - Create new repository secret named `VSCE_PAT`
   - Paste the PAT as the value

The GitHub Action will automatically:
- Build the extension
- Run tests
- Publish to VSCode Marketplace when changes are pushed to main

## Recommended VSCode Extensions

- [Vitest](https://marketplace.visualstudio.com/items?itemName=ZixuanChen.vitest-explorer)
- [Volar](https://marketplace.visualstudio.com/items?itemName=Vue.volar)
- [I18n A11y](https://marketplace.visualstudio.com/items?itemName=Lokalise.i18n-ally)
- [Iconify Intellisense](https://marketplace.visualstudio.com/items?itemName=antfu.iconify)

## References

- [Webviews](https://code.visualstudio.com/api/extension-guides/webview)
- [UX Guidelines](https://code.visualstudio.com/api/ux-guidelines/overview)
- [Webview view API](https://code.visualstudio.com/api/references/vscode-api#WebviewView)
- [Theme Guidelines](https://code.visualstudio.com/api/references/theme-color)
- [8x9craft2 API](http://wiki.craft2.8x9.jp/wiki/Category:APIs)
