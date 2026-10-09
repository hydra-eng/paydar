# Local risk record import

FraudCheck starts empty. Under Privacy / integrations, import a JSON document from a source you have independently vetted. PayDar validates the document's structure, but does not authenticate the source or establish that its allegations are true.

The top-level fields are:

| Field | Type | Meaning |
| --- | --- | --- |
| `source` | String, 2–120 characters | Human-readable provenance label |
| `expires` | ISO date (`YYYY-MM-DD`) | Source's validity date |
| `records` | Array, at most 5,000 items | Each item has `upi` and `reason` |
| `records[].upi` | Conventional UPI ID | Normalized case-insensitively |
| `records[].reason` | String, 3–300 characters | Reason for the source's report |

Maximum document size is 1 MB. Duplicate IDs and malformed entries are rejected. Import replaces the previous local list atomically; an invalid document leaves the existing list intact. Expired matches are visibly marked. Matches are reported records, and missing records remain unverified.

Use the official I4C suspect search for an independent online lookup. It opens in the browser, where the user completes the official flow. The app does not scrape it or bypass its CAPTCHA.
