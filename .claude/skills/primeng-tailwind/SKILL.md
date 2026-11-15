---
name: primeng-tailwind
description: Rules for combining PrimeNG (Aura preset, styled mode) with Tailwind CSS 4 in the card game frontend. Covers setup, CSS layer order, shared colour tokens via tailwindcss-primeui, when to use PrimeNG vs plain Tailwind, dark mode, and accessibility. Use when styling or building UI in frontend/.
---

# PrimeNG + Tailwind 4 (card game)

## Setup

```bash
npm i primeng @primeuix/themes primeicons
npm i -D tailwindcss @tailwindcss/postcss postcss tailwindcss-primeui
```

`.postcssrc.json`:
```json
{ "plugins": { "@tailwindcss/postcss": {} } }
```

`src/styles.css`:
```css
@layer theme, base, primeng, components, utilities;
@import "tailwindcss";
@plugin "tailwindcss-primeui";
@import "primeicons/primeicons.css";
```

`app.config.ts`:
```ts
import { providePrimeNG } from 'primeng/config';
import { definePreset } from '@primeuix/themes';
import Aura from '@primeuix/themes/aura';

const CardGamePreset = definePreset(Aura, {
  semantic: { primary: { 50: '{emerald.50}', /* … */ 950: '{emerald.950}' } },
});

providePrimeNG({
  theme: {
    preset: CardGamePreset,
    options: { darkModeSelector: '.app-dark', cssLayer: { name: 'primeng', order: 'theme, base, primeng' } },
  },
  ripple: true,
}),
provideAnimationsAsync(),
```

- Check the current PrimeNG docs for the installed major version before setup. Package names and
  config keys have changed between majors (`@primeng/themes` → `@primeuix/themes`).
- The CSS layer order is what lets Tailwind utilities override PrimeNG styles without `!important`.

## Which tool for what

| Need | Use |
|------|-----|
| Page layout, grids, spacing, flex, responsive breakpoints | **Tailwind** |
| Buttons, inputs, dialogs, toasts, tables, menus, tooltips, avatars, badges, progress | **PrimeNG** |
| The playing cards and the game table (custom visuals) | **Tailwind** in your own components |
| Colours anywhere | **PrimeNG tokens through Tailwind**: `bg-primary`, `text-primary-contrast`, `bg-surface-0 dark:bg-surface-900`, `text-muted-color`, `border-surface` |

- Never hard-code hex colours or Tailwind palette colours (`bg-emerald-500`) in components. Use the
  semantic tokens from `tailwindcss-primeui` so the theme and dark mode stay consistent. The only
  exception is game-specific card colours (red/blue/green), defined once as CSS variables in `styles.css`.
- Customise PrimeNG through **design tokens** in the preset or the component's `dt` input. Use the
  `pt` (pass-through) API to add Tailwind classes to inner elements. **Never** override PrimeNG
  internals with global CSS selectors like `.p-button .p-button-label`.
- Don't wrap simple PrimeNG components in your own components without a reason. Wrap them only when
  you add behaviour or reuse a fixed configuration.

## Component guidance for this app

- Feedback: one `<p-toast />` in the root layout, fed by a `NotificationService` that wraps `MessageService`.
- Invitations: `p-dialog` or a `p-toast` with accept/decline actions. Online players list: `p-listbox` or `p-dataview`.
- Profile / history: `p-table` with server-side paging (lazy mode).
- Timer: `p-progressbar` or `p-knob` (read-only) driven by a signal.
- Confirmations: `p-confirmdialog` (`ConfirmationService`). Never `window.confirm`.
- Icons: `primeicons` (`pi pi-*`). Don't add a second icon set.

## Dark mode

- Toggle the `.app-dark` class on `<html>`. Store the preference in `localStorage` and default to
  `prefers-color-scheme`.
- Tailwind's dark variant must use the same selector: `@custom-variant dark (&:where(.app-dark, .app-dark *));`

## Accessibility

- PrimeNG components are accessible when used correctly. Always pass `ariaLabel` to icon-only buttons
  and link labels with `inputId`/`for`.
- Colour contrast must meet WCAG AA in both themes.
- Never show card meaning (colour, shape) through colour alone. Add the shape or letter and an `aria-label`.
