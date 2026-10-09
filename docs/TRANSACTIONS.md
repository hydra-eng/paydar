# Unified transaction history

Open **Payments** in the bottom navigation, or **Transactions** on the Radar home screen. New installations have no example payments.

## Add records

- **Payment notifications:** select the payment apps you use, enable capture, and grant PayDar notification access in Android settings. Predefined package labels cover Google Pay, PhonePe, Paytm, BHIM and Amazon Pay. Another payment app can be added by its Android package name. This selects sources; it does not imply that every notification format has been validated.
- **SMS:** explicitly approve import and SMS permission. Up to 1,000 inbox messages from the last 90 days are inspected; only supported UPI receipt messages are imported. SMS often identifies a bank/recipient without identifying the UPI app. The app label stays “Not identified” unless another source or your correction supplies it.
- **Paste or Share:** paste transaction text, or share plain text into PayDar from another app. Review the actual receipt date and optionally label its source app. Sharing an image/PDF is not supported by this transaction importer; image QR scanning remains a separate ScanSafe flow.
- **Manual:** enter an actual payment with its date, amount, direction, status and optional ID/reference.
- **CSV:** import an explicit PayDar-format file, up to 2 MB and 10,000 rows. Export an empty template from the screen or use the header below. Arbitrary bank/app statement formats need mapping to these columns; the importer does not guess their schema.

```csv
date,amount,direction,status,party,upi,reference,app,source
```

`date`: ISO instant (`2026-10-05T12:00:00Z`) or `YYYY-MM-DD` interpreted in the phone's timezone. `amount`: positive INR decimal with up to two fractional digits. `direction`: `DEBIT` or `CREDIT`. `status`: `SUCCEEDED`, `PENDING` or `FAILED`. `party`: required. `upi`, `reference` and `app`: optional. Imported provenance is labelled CSV regardless of the original source column. Quoted fields, commas inside quotes, escaped quotes and CRLF are supported; invalid rows are counted and skipped. Malformed headers/quoting fail before writing.

## Review and deduplication

Search by party, app, UPI ID or reference. Filter by sent/received, app and current month. Totals include only imported `SUCCEEDED` receipts in the current filters; they do not represent account balances or bank verification. Edit incorrect fields or delete unwanted records.

Matching reference + direction + amount merges sources into one receipt. Pending alerts cannot replace an existing terminal status. Opposite directions remain separate so transfers between your accounts are not silently discarded. Records without a shared reference are deduplicated by a source fingerprint; the same payment observed across SMS and notifications can remain separate when both lack a usable reference. Review such records manually rather than merging unrelated payments with the same amount.

## Consent and storage

Capture and app selection are disabled/empty initially. Android notification access can expose notifications from all apps; PayDar checks the enabled source list before extracting receipt content. OTPs, collection requests, scheduled-debit notices, unrelated content and ambiguous directions are ignored. Raw SMS/notification text is not persisted; extracted fields and fingerprints are stored privately.

Disabling capture stops new notification records. **Private → Delete all PayDar data** removes transaction history as well as existing categories and disables capture/reminders. It cannot delete exported files or original receipts in other apps. CSV export intentionally writes payment details to the location you choose and neutralizes spreadsheet formula prefixes.

## Coverage and verification

Only supported visible receipt text can be extracted. Notifications can be disabled, redacted, changed by an app update, absent in work profiles, or unavailable on a device. Past private UPI app histories are inaccessible to PayDar; SMS/CSV/manual records provide the historical import path. No payment is initiated, reversed or verified by this ledger.

The beta uses the same debug signing certificate as 0.1.0 and includes a non-destructive SQLite v1→v2 migration. Automated receipt/storage tests pass. No Android hardware is attached, so notification delivery, share flows, permissions and UI still need physical-device validation.
