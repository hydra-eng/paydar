# PayDar feature audit — 10 October 2026

The original deck is a product concept. Its five core tools exist in the beta, with the limitations below disclosed in the app. Planned dependencies are not completed integrations.

| Deck capability | Current app | Unfinished work |
| --- | --- | --- |
| ScanSafe | Camera/image UPI QR decoding and expected-recipient comparison | Physical sticker-over-sticker detection, validated OpenCV model, representative field testing |
| FraudCheck | Imported local lists, source/expiry display, unknown verdicts, check history | Licensed ScamDekho or other authoritative feed, authenticated offline updates, coverage/expiry policy |
| MuleGuard | Three-question rules with explanations | Expert validation and multilingual field testing; AI pattern inference is absent |
| WrongPay Rescue | Saved support drafts, share sheet and official escalation link | Provider integration for actual filing and case-status tracking |
| SubWatcher | Local SMS/paste parsing, user-confirmed records, monthly estimates, reminders | Model-based extraction, verified recurrence detection across billing periods, personalized on-device language generation |
| On-device AI | Transparent rules only | Real licensed Gemma/Phi model, LiteRT/runtime integration and Snapdragon/NPU evaluation; sub-800 ms performance is unmeasured |
| Binary “safe to pay” verdict | Unknown remains unverified | A safe verdict is intentionally not issued from an incomplete list; broader data/model coverage still cannot guarantee every payment |
| Production launch | Installable debug APK | Device testing, release signing, current target-SDK/store compliance and restricted-SMS permission review |

## Added: unified transaction history

Android beta 0.2.0 adds a Payments tab and a Transactions tile on the home screen. It combines supported payment receipts from selected app notifications, consent-based UPI SMS imports, pasted/shared text, manual entries and PayDar-format CSV.

The history includes amount, direction, receipt status, counterparty, UPI ID when present, payment app when known, timestamp, reference and source. It offers search, app/direction/month filters, successful-receipt totals, correction, deletion and CSV export. All records are stored in the existing private SQLite database; upgrading from beta 0.1 preserves prior records.

See [TRANSACTIONS.md](TRANSACTIONS.md) for setup, coverage and import format. These are user-provided/source-reported receipts, not bank-confirmed statements. The feature does not read private databases from other payment apps or retrieve complete historical app records.

## References

- Android app isolation: https://source.android.com/docs/security/app-sandbox
- Notification listener contract: https://developer.android.com/reference/android/service/notification/NotificationListenerService
- User-selected file access: https://developer.android.com/training/data-storage/shared/documents-files
- SMS policy: https://support.google.com/googleplay/android-developer/answer/10208820

## Added in beta 0.3

Optional Firebase Google authentication, profile display, sign-out and reauthenticated cloud-account deletion. Local payment data is not synced or partitioned by account. The Google provider and debug SHA-1 are configured in Firebase project `paydar-hydra`; device sign-in verification remains pending. Public website: https://paydar.web.app.
