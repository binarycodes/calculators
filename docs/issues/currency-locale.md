# Number-to-words is tied to the currency locale, not the UI locale

**Status:** Postponed
**Area:** i18n / money formatting
**Raised:** 2026-06-25
**Related:** internationalization work (translations bundle, `AppLocaleConfig`)

## Summary

`NumberToWords.amountInWords` spells amounts out correctly today, but it is not
wired to the application's i18n locale. Two gaps mean it won't follow a future
non-English UI language:

1. **Spelling uses the currency's locale, not the session locale.** The spell-out
   formatter is built from `SupportedCurrency.locale()` (`en-IN`, `en-IE`,
   `en-US`) rather than `UI.getCurrent().getLocale()`. All three are English
   variants, so adding e.g. a German UI locale would still produce English
   words ("Seventy-Five Thousand"). The Indian Lakh/Crore vs Western
   Thousand/Million split is correct, but it tracks the currency, not the user.
2. **The currency word is a hard-coded English constant.** `SupportedCurrency.word()`
   returns the literals `"Rupees" / "Euros" / "Dollars"`, appended directly. This
   bypasses the translation bundle entirely.

## Where

- `src/main/java/io/binarycodes/calculators/base/money/NumberToWords.java`
  — `amountInWords(...)` builds `new RuleBasedNumberFormat(supportedCurrency.locale(), SPELLOUT)`.
- `src/main/java/io/binarycodes/calculators/base/money/SupportedCurrency.java`
  — `locale()` (currency locale) and `word()` (hard-coded English suffix).

Surfaces in the `MoneyField` helper text and in money-cell tooltips across the
projection grids (e.g. "Three Thousand Five Hundred Dollars").

## Why postponed

This is deeper than a string swap. Externalising just the currency word while the
number spelling stays English would be inconsistent. Proper support means driving
`RuleBasedNumberFormat` from the UI locale (with a sensible per-currency
numbering-system choice) and localising the currency word — only worth doing
when a non-English locale is actually added. It was deliberately left out of the
en_GB i18n pass, which the app pins to anyway, so there is no visible defect today.

## When to revisit

When adding a second UI language. At that point:
- Spell out using the active UI locale (or a configured numbering system),
  not the currency's locale.
- Move the currency word into the translation bundle (or derive it from the
  locale's currency display name).
