# Reference implementation: NadiPay to PayDar

The supplied HTML is a visual prototype. Its balances, bank connections, usage histories and cancellation confirmations are sample values or toast messages.

| Reference feature | PayDar implementation |
| --- | --- |
| Unified transactions | Share/paste receipts, CSV and manual records; search, filters and editing |
| Home totals | Current calendar-month successful sent/received receipts |
| Spending chart | Seven local calendar days, including zero days |
| Insights | Previous full-month sent total and current source-app breakdown |
| Money Guard | Same recipient/amount on multiple days within 90 days, labeled for review |
| Subscriptions | User-managed local entries; does not cancel real mandates |
| Support | Existing local recovery-report preparation and sharing |
| Bank balance and linked accounts | Not connected; requires an authorized financial-data integration |
| App usage / unused subscriptions | Not available; no usage access requested |
| Mandate cancellation | Must be completed in the provider’s app |

Only successful receipts contribute to totals. Pending, failed and future-dated receipts are excluded. Guard patterns are not fraud verdicts or confirmed subscriptions. Imported data can be incomplete.
