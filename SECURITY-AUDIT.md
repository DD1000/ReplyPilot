# Reply Pilot security audit — September 25, 2026

## 0.11.0 Simplified Autopilot and full-history analysis

The owner explicitly authorized sending full available SMS/MMS text to OpenAI for initial analysis. A separate worker snapshots the private archive into encrypted per-contact batches, preserving long texts via fragments. It checks default-role/read access, pairing revision and recipient/source identity before and after requests. Summaries and consumed-batch cursors commit atomically; interruption resumes the same request identity, and corruption requires rebuilding. The readiness flag cannot become true until all queued text is processed. No chat is enabled and no historical incoming message is scheduled merely by importing or analyzing it. New live incoming jobs wait for readiness and recheck their source before resuming.

Autopilot still requires per-contact standing permission, adequate history, the global switch, valid Android access/SIM, current recipient/source, notification access and manual-takeover checks. It responds to ordinary acknowledgments, so another auto-reply system can still generate repeated real incoming turns; duplicate protection prevents reprocessing the same turn, not every possible two-bot loop. Plans and unsupported/uncertain requests produce bounded neutral deferrals and submission-bound attention notifications. Model judgment is fallible and no claim of perfect personal imitation or factual correctness is made.

Full-history and new Autopilot requests use authenticated endpoints, fixed OpenAI Responses, no tools/redirects, strict schemas and store:false. The relay retains existing shared per-minute/daily/in-flight quotas and short request deduplication; no conversation database or body logging is added. The host and OpenAI receive the message text, including any personal information present in it. The app adds no credential value or contact name/number fields to analysis batches. Stored summaries are historical context, never evidence of current location, availability or commitments.

The v18 migration preserves owner drafts/manual-send records and disables legacy draft-only modes rather than granting automatic send permission. Obsolete attention/sleep jobs cannot restart their former flows. Only newly completed live MMS downloads can create new automatic MMS work; imported records, retries and repairs do not. Exact source identity and own-outbox exclusion are checked before sending. No new app permission or signing key is introduced. Physical-device and live-model checks remain necessary.

## 0.10.4 Manual reply precedence

A deliberate Send tap approves the entered text for the verified single recipient. It no longer waits for AI, full chat hydration or a previous carrier receipt. Native foreground, default-role, SMS permissions, current recipient, SIM and location-provenance checks remain. The immediate manual path reads the current provider state; previously approved timers retain their existing stale-message checks.

Manual takeover is saved per conversation before remaining send preflight. It pauses unsent automatic/plan replies, retires queued attention actions and draft jobs, and invalidates in-flight text, MMS-text and media generation. A failed manual send does not hand the same incoming turn back to AI. An outgoing change, provider deletion or older history cannot release the takeover; only verified newer incoming activity can. Carrier-submitted messages remain submitted and cannot be recalled.

Each manual text request carries a UUID linked transactionally to its send job before dispatch. Repeating that request returns the same outcome without another carrier call. A new deliberate send may contain identical text. Database version17 adds takeover/request records without deleting existing history, pairing or settings. UI request revisions prevent late AI/history responses from replacing newer owner text. No new permissions, external service or credential exposure is introduced. Validation and device limitations are recorded in VALIDATION.md.

SMS receipt tokens and a durable MMS receipt sequence distinguish messages already received before takeover from later arrivals. MMS download-store version2 retains receipt identity through provider insertion/recovery; exact provider binding is required before extending a takeover watermark. The migration preserves existing download state and carrier callback tokens. Unknown receipt order remains conservative.

## 0.10.3 Live inbox updates

The new live-inbox read is display-only. It does not change send bases, drafts, contact AI settings or timer approvals. Bounded current SMS/MMS summaries merge into the visible list independently of slow full snapshots; an incomplete fast read cannot delete older conversations. Permission/role/lifecycle checks are applied to delivery, and UI merge ordering prevents an older response from replacing newer display data. Existing touch-target preservation and contact-photo privacy remain in place.

Incoming SMS is saved on a separate serial receipt worker, retaining provider insertion, burst binding and send-lock checks. Incoming MMS, download completion, retry, recovery and sent callbacks move together onto a single serial MMS worker, preserving their existing mutual ordering and private-file cleanup protections. Its UI invalidation occurs after the row is committed. This changes work scheduling, not which messages qualify for automatic replies. No new app permissions, outbound service or AI request is introduced.

## 0.10.2 Text MMS classification and source-bound drafting

MMS transport is distinct from attachment content. Complete text/plain parts plus structural SMIL can qualify as text; missing, invalid, rebound, unreadable or truncated parts remain unknown. Actual attachment parts keep media handling. A dedicated foreground text-draft path verifies a single recipient, complete unanswered context, current mixed SMS/MMS position, unchanged provider fingerprints, exact owner draft, profile/configuration revisions and live access. Pending work, plans, unsuitable questions, personal-location questions, bedtime and repeated acknowledgments stay guarded. This path never schedules or sends. Existing automatic SMS gates still reject newer incoming MMS.

Generated text-MMS drafts carry source ID, date, text and address in database version16; reads suppress a draft when its source is no longer the same latest incoming text. A deliberate owner edit replaces that binding with the normal owner draft. No migration discards existing stored text or profiles. Inbox rendering preserves a touch target until the gesture ends; permission revocation still clears private content immediately, and no native obscured-touch protection is weakened.

## 0.10.1 Send outcomes and native text selection

Carrier proof is monotonic: complete delivery settles its exact job to sent, and stale callbacks/read responses cannot restore the sending state or downgrade delivery. Recovery repairs existing inconsistent rows while retaining multipart and job/URI/recipient binding. Submission, draft editing and callback execution no longer queue behind inbox work; the same final SEND_LOCK, role, permission, SIM, recipient and fresh-message checks remain. No automatic retry is introduced. Optional learning runs after carrier dispatch and verifies the exact original SMS identity before and after its context read.

Fast status responses cannot create chat history from arbitrary past jobs or move a newer incoming-message boundary backwards. Only a directly committed send response may immediately add its own outgoing bubble; final native sending always revalidates the current provider. A fast generated-draft response is bound to the requested thread/base and live-access epoch.

The Explain-message UI/bridge is removed. Native selection callbacks continue to delegate to WebView. Handled Copy/Cut finish only their own toolbar; Select all remains native. Rejected obscured gestures are cancelled completely and cannot click through when a toolbar disappears. Android's normal HIDE_OVERLAY_WINDOWS permission hides third-party overlays; no runtime prompt is introduced. MotionEvent flags do not identify an overlay owner, so obscured touches are still rejected. Physical Pixel toolbar/IME behavior remains untested.

## 0.10.0 Contact guidance, selection and plan deferral

New context fields are bounded locally and on the relay and treated as untrusted quoted data. The new client identifies learned-voice requests; old client behavior remains compatible. Prior style fields remain at rest but are excluded from new-client model guidance. The relay still has no SMS endpoint or carrier access.

Delay answer is off by default and requires a verified eligible contact, existing cloud/automatic consent, global automation, and the explicit per-contact setting. It reuses the native attention-action send path and a durable once-per-planning-hold attempt marker. Quiet-period, current incoming-message/recipient binding, profile/Sleep revisions, bedtime, draft/attachment, permission and role gates are checked before dispatch. The canned acknowledgment does not clear the planning hold. Interrupted/uncertain dispatch is not replayed automatically. No new permissions are requested; the added job service is private and protected by BIND_JOB_SERVICE.

Focused fields remain attached across compatible UI refreshes. Selection dismissal and keyboard bridge actions are confined to the packaged document and known active editors; native visibility/foreground checks apply. The existing obscured-touch protection is retained. Local AI generation, downloads and ML Kit runtime dependencies are removed.

Validation results and physical-device limitations are recorded in VALIDATION.md. This review does not guarantee Android keyboard, carrier or unattended delivery behavior on an untested device.

## 0.9.13 Automatic-reply history evidence

Replaced the latest50 SMS-only eligibility source with bounded live SMS/MMS text. The minimum remains20 distinct turns including5 per speaker. Both transports receive scan opportunities; repeated/provider+import overlaps are deduplicated. Known automatic/Delay SMS outputs are excluded with identity-bound Store lookups; MMS IDs remain a separate transport namespace. Only received and actually sent messages qualify; recipient mismatches, unsent rows, notifications and media-only placeholders cannot authorize automatic replies.

No saved/display cache authorizes automatic sending. Current role/read permission and recipient are checked before and after collection; background automatic checks remain possible. Per-pass row/text/part bounds and early threshold exit limit work and memory. Incomplete scans are identified honestly. Optional readiness failures no longer abort a successfully loaded chat/profile, but report unavailable and eligible:false; strict profile/background/dispatch checks retain failure behavior. Recheck is read-only and does not enable a mode, send a message or call AI. Unsaved contact notes and logs remain intact.

531 native tests and three mock-browser checks passed. Physical Pixel provider/grant/carrier behavior remains untested locally. No new permissions, external data transfer, backend change or credential exposure.

## 0.9.12 Android share receiving

The exported main Activity now accepts SEND and SEND_MULTIPLE for supported text/media categories. Incoming content is untrusted: bounded plain text, bounded URI/item counts, content scheme only, explicit read grants checked independently of Reply Pilot's broad SMS/Contacts permissions, and private providers blocked. Only the Contacts provider's exported vCard paths may pass with an explicit grant. External files keep the existing byte-signature, size, image and staged-total limits.

Sharing does not invoke AI or sending. The owner selects a single validated recipient and explicitly adds to their manual draft; address, latest SMS base and existing body are rechecked before commit. Existing text is appended without truncation. Adding a share cancels automatic timers and invalidates concurrent AI generations. Attachment preparation publishes a batch or rolls back new assets. Cancellation remains available during slow provider reads and invalidates the pending commit.

Sanitized activity intents, saved-state restore and a bounded durable consumed-id set guard recreation/replay. Native replay returns identity only; the UI reloads authoritative draft content and preserves newer edits. Results require foreground messaging access at delivery. No new permissions, server endpoint or credential distribution changes. Unit and mock-browser checks passed; actual Android grant/lifecycle and carrier tests remain outstanding.

## 0.9.11 History recovery

- Live conversation/history/media-context requests use two readers with a four-item queue. Foreground, default-SMS, READ_SMS and activity access epoch are checked before execution and WebView delivery. Send, AI generation and profile mutations stay on their prior lanes. Cached messages still cannot authorize sends, AI, raw attachments or message-meaning changes.
- A blocked archive may return only a validated empty pending envelope with matching live permissions and revision. Saved private content remains blocked until a completed scan. Stable snapshots advance for actual message/header/recipient/deletion changes; independent scan markers retain deletion reconciliation across unchanged scans and clock rollback. No encryption/schema/identity changes.
- UI rejects late archived results after a completed live read, preserves failed paging boundaries, rebases a stale snapshot at most once and checks returned recipient identity. Read-only history pagination can precede full inbox initialization; send/AI controls keep their existing full readiness checks. Slow-read retries preserve typing and ignore superseded callbacks.
- Mark-read writes occur only when unread/unseen messages exist, with a current foreground/default-role/SMS check before each write. Optional provider-write failure does not discard successful history; permission errors still fail closed. No new permission, export, endpoint, credential or automatic AI/send behavior.

## 0.9.10 Default SMS setup recovery

- Success depends only on Android's current default-SMS role, with READ_SMS and SEND_SMS checked separately. No result code, timeout, UI checkmark or cached state grants permissions or bypasses native message/send checks.
- The fast status action runs on the UI lane without querying message contents. Each chooser has a unique request code; old callbacks cannot complete a retry. Return reconciliation and a 30-second chooser deadline release stale waits, while the explanatory dialog still waits for the user. Opening Android Settings retires the pending attempt; destroy removes its timer/dialog.
- UI status applies immediately, ignores late request/status/snapshot results, and restores display access only after live role plus read/send checks succeed. Losing role or message permissions clears private displayed content. No AI request, message send, new permission, exported component, database migration or credential change is introduced.

## 0.9.9 History reconciliation and MMS practice

- SMS/MMS text context for explicit practice is selected chronologically, capped at 50 turns/600 characters each, with one older incoming fallback if necessary. Native thread/recipient validation, current-message fingerprints, session/turn idempotence, access epochs and before/after-network checks remain. Unsent messages, notices, other contacts, empty media and unreadable/partial MMS texts cannot become training examples. No new automatic cloud request or sending authority.
- The shared MMS text reader accepts only selected normalized text/plain parts, uses bounded byte reads and charset decoding, and preserves permission errors. Missing text files produce an unavailable marker instead of aborting the full archive or inventing text. Archive replacement remains transactional, retains the old snapshot on provider failure and surfaces generic status with bounded backoff. Counts clear when message access is unavailable; explicit refresh requires foreground/default-SMS/read access.
- UI history merging preserves loaded rows, verifies recipient identity before keeping archive data, fills cached/live gaps and advances pagination only on verified success. Cache pages do not grant meaning/media/send authority. Provisional media cannot open raw part URLs until fresh validation. A known-unavailable text row is labeled explicitly; no stale text is promoted as current. No new permissions, credentials, hosted endpoint or database migration.

## 0.9.8 Contact photos in saved conversation rows

The launch summary response now attaches local contact-photo routes after reading and normalizing its encrypted display cache. The stored schema still excludes image data and photo URLs. Current Contacts/SMS/default-role/foreground checks in ContactPhotos.annotate and the existing launch-result delivery check remain. The first twelve rendered conversation thumbnails request eager loading; later ones remain lazy, including after photo invalidation. Every image uses the existing strict local route and bounded decoder/cache with access checks at consumption; missing photos fall back to initials. No external photo service, permission, sending behavior or credential changes.

## 0.9.7 Independent practice loading

Practice uses separate bounded state and generation workers; generation has no pending queue, so repeated taps cannot accumulate paid requests. Read-only state uses immutable session snapshots without the shared send lock. The bridge captures access before enqueue, rechecks it before execution and at actual UI delivery, and checks again around network generation. The visible recipient is supplied with every practice request and compared with the current canonical contact. Existing session/turn identity, settings revisions, message freshness and no-send behavior remain. Practice dialogs close on pause/access changes and ignore responses after navigation. Reopened sessions poll only local saved state, with a bounded retry window; opening or polling never generates cloud requests. No new permission, endpoint, credential or model change.

## 0.9.6 Media gallery classification

Media gallery membership now depends on a valid attachment part, not the MMS transport label. Normalized plain text, HTML, SMIL presentation parts, multipart containers, missing/invalid MIME types and invalid part IDs cannot create a media card. Drafts, pending notifications and protocol reports are excluded from the gallery; existing chat history/download handling is unchanged. The native query filters before counting the 60-card window and before contact lookup; the UI also filters returned/stored gallery data. Captions remain escaped beside real attachments. No permission, credential, remote service, message mutation or sending behavior changes.

## 0.9.5 Full local archive, contact pictures and independent settings

- The full archive uses an app-private no-backup SQLite database with WAL paging. Each content record is AES-256-GCM encrypted with an Android Keystore key and authenticated message/thread identity; provider IDs, dates and content hashes are indexing metadata. Counts are not capped, but individual records and returned pages are bounded. SMS/MMS text and media descriptors are saved; media file bytes, drafts, credentials, AI consent and sending authority are excluded.
- A separate low-priority worker streams provider rows and replaces the snapshot transactionally. Failed/incomplete reads keep the previous committed archive; successful scans reconcile deletions. Fresh SMS/default-role checks guard reads, writes and UI delivery. Access loss blocks delivery and schedules deletion; Contacts loss scrubs names. Cached history is display-only, scoped to the expected recipient and snapshot generation, and is superseded by live results. The first full sync requires time and available phone storage. No background cache work reads messages aloud, marks them read, invokes AI or sends messages.
- Contact photos use a strict local HTTPS route containing only a normalized phone number. Native phone lookup resolves the Android contact ID and reads its stored thumbnail without following supplied URIs or performing network requests. Permissions, default role, foreground and invalidation generation are checked before lookup and stream delivery. Input/decoded images, concurrent decodes and RAM caching are bounded; failed/missing pictures fall back to initials. Contact changes invalidate thumbnails and refresh visible photos.
- Reply setup has a separate worker, independent of full history. Current canonical recipients and profile revisions guard settings saves; cached history never grants editing or sending authority. Draft/AI/send controls still require their existing live checks. No new permission, exported component, FileProvider root, hosted endpoint or secret is introduced.

## 0.9.2 Encrypted recent histories and snippet correction

- The new cache stores only display text/metadata for up to10 most-recent conversations and30 eligible chronological messages each. It uses a distinct Android Keystore AES-256-GCM key, authenticated format, bounded2MiB plaintext and atomic app-private no-backup file, outside every FileProvider root. Message text is capped at2048UTF-16 units with a visible shortened flag; attachment bytes/part references, drafts, profiles, credentials, sending context and AI consent are not included.
- The capture worker is separate from UI, message operations and AI. It reads actual SMS/MMS rows, filters drafts/protocol reports, performs no read-marking and coalesces newer requests. Permission/default-role, sequence and provider epochs guard reads/writes; access loss hides and deletes previews. Contact names are scrubbed from delivered and persisted pages when contact access is lost, including cold-start loading. Failed or partial reads cannot overwrite the last valid cache; successful authoritative-empty input removes it.
- UI caches remain provisional: no send/AI/profile permission comes from them, links are inactive until live hydration, later live results take precedence and absent threads are discarded. Current access is rechecked before native delivery. Typed text remains separate and acquires a live message base before saving/sending. Cache warm-up performs no network, AI or contact messaging.
- Inbox MMS snippets now read bounded text/plain parts, with a small bounded file-backed text fallback where needed; media payloads are not decoded. SMS/MMS timestamp conversion and latest-row eligibility keep drafts and protocol reports out of the list. Old generic-only summary snapshots are invalidated once. This feature changes no remote service, key, pairing or automatic reply authorization.

## 0.9.0 Launch preferences and confirmed home

- The saved theme is restricted to ten identifiers and injected into the packaged document before styles or scripts run. No raw preference becomes markup. A separate ordered worker acknowledges durable theme writes; unrelated settings cannot overwrite the theme. Theme and pin saves roll back in-memory preferences when disk commit fails. Pin existence checks no longer hold the preference read lock during provider work. Optimistic UI changes carry mutation/read barriers, and saved launch rows use current durable pin IDs.
- Home lookup is explicit and foreground-only with precise permission, enabled location replies and phone location services. It obtains a non-mock fix with at most50 metres reported accuracy, requires freshness within two minutes on wall/elapsed clocks and current boot, and uses bounded Android geocoding plus a fixed HTTPS OpenStreetMap Overpass endpoint for actual numbered street records within250 metres. Results are deduplicated, ordered by distance and capped at ten. Addresses and distances are shown; candidate coordinates stay native. Maps cannot guarantee residential use, rooftop accuracy or complete coverage.
- Lookup does not change saved home or sharing consent. Confirmation uses a short-lived request ID and candidate ID bound to the fix, foreground state, permissions and settings revision. Commit rechecks these under the existing send lock, saves the selected address/coordinates, and invalidates prior location-dependent work. Cancellation/pause/settings changes invalidate tokens and cancel pending location/map/geocoder work. Manual entry preserves cleaned apartment/unit details while geocoding validates a numbered street. Late UI results preserve newer typing.
- Coordinates are sent to the existing geocoding/map providers for the explicit lookup, as disclosed in Settings. The address is saved privately on-device and excluded from AI location context; existing contact opt-ins and general-location response limits remain. No new permission, exported component, cloud deployment, pairing change or credential is introduced. Tests use fictional locations and do not establish GPS, geocoder coverage or physical Pixel behavior.

## 0.8.9 Public link previews

- Link previews are display-only and separate from AI drafting. The native request binds a strict thread, transport kind, provider message ID and canonical URL to actual SMS text or MMS text parts both before and after the fetch. Provisional inbox snippets are not linkified, preventing a truncated cached URL from becoming a destination. Only visible cards in a fresh open chat fetch automatically; the settings switch disables fetching and clears cached previews. Explicit link taps use validated HTTP/HTTPS ACTION_VIEW + CATEGORY_BROWSABLE intents for the original URL, never a page-supplied canonical/redirect URL or executable URI scheme.
- Native network requests use public HTTPS on port443 only, with strict URL/credential/control validation and public-address validation of every DNS result, literal IP, redirect and image destination. IPv4 private/link-local/loopback/reserved addresses and IPv6 local/transition/documentation ranges are rejected. Manual bounded redirects preserve TLS requirements. Proxying, cookies, authorization, JavaScript, automatic redirects, connection reuse and HTTP2 coalescing are disabled for this client. Websites and their public image hosts see a normal network request and the phone’s IP; the feature does not transmit the rest of a message, contact information or the OpenAI credential.
- HTML is read as a bounded prefix and parsed as passive markup by jsoup. Open Graph/Twitter/title metadata becomes escaped text, with the actual URL host visible. Images have bounded transport bytes and pixel dimensions, are resized and re-encoded as JPEG without source metadata, and are served only through an opaque, access-checked local image route. Remote pages/scripts/images never load directly in the privileged WebView; its CSP and blocked network loads stay in place. Login/error pages or unavailable metadata leave an ordinary link card.
- Two background workers and bounded queues, DNS waits, response sizes, redirects and a total fetch budget prevent slow links from using the messaging or AI worker. Metadata/images are ephemeral bounded memory caches; no link-preview file archive is created. Foreground, permission, role, setting and generation checks reject stale work. Activity request epochs cover queued-before-pause requests as well as final delivery. Contact-message deletion or changed text during a read invalidates its result. Sender/AI authorization is not derived from a preview.
- This adds jsoup1.23.2 and the Java API desugaring support recommended for Android, with the compatible AGP8.13.2 compiler patch. Reference contracts: [Open Graph metadata](https://ogp.me/), [Android browser intents](https://developer.android.com/guide/components/intents-common#Browser), [jsoup Android installation](https://jsoup.org/download). See VALIDATION.md for actual tests and remaining physical-device checks. This scoped review does not certify every remote website, Android image decoder or external link destination.

## 0.8.8 Encrypted launch inbox

- A dedicated launch worker reads at most 20 locally saved conversation summaries without querying the message provider. The file uses AES-256-GCM with an app-specific Android Keystore key and authenticated format identifier, in the private no-backup directory. It is outside FileProvider roots and excluded from Android backup. Plaintext is bounded to 64 KiB; row fields and snippets are allowlisted and bounded. Full histories, attachments, drafts, profiles, permissions, jobs and AI input are not persisted in this file.
- Only a successful authoritative inbox read can replace the saved rows. Selection is by the latest message date, before any pin ordering; an authoritative empty inbox removes the file. Atomic writes are coalesced on a separate worker. Missing keys, damaged data or storage failures fall back to live loading without blocking message operations. A temporary provider failure is not treated as an empty inbox.
- Load, write and final WebView delivery recheck message permission and default-app role. Access changes invalidate older results; access loss schedules file deletion. Contact loss scrubs saved names. UI startup guards reject late snapshots and never import sending authority, SIM choices, cloud consent or profiles from cache. A fresh global snapshot and live conversation are required before send/AI actions become available; owner typing stays buffered to the correct thread.
- The saved list may briefly lag messages changed while the app was closed. It is display-only and is replaced by the live list, including deletions. Android Keystore, process-restart persistence and physical launch timing still need device validation; desktop bridge tests do not prove those platform behaviors. No new permission, exported component, service deployment or credential change is introduced.

## 0.8.7 Conversation previews and responsive navigation

- Background warm-up reads bounded history pages on a separate low-priority worker. Previews stay in limited process memory and do not mark messages read, prepare/send replies, or call AI. Media bytes are not prefetched. The authoritative live conversation still performs fresh provider reads before send/AI controls become available.
- Native preview results use a generation/access stamp, bounded LRU storage, expiration and a final permission/role check before WebView delivery. Provider changes, permission results, foreground transitions and app message events invalidate native previews. Contact changes also invalidate names. The UI independently rejects late responses and clears private caches after message access is lost.
- Immediate navigation queues captured draft saves without waiting for storage. Writes retain their original thread/base identity; new typing during a preview is buffered until the live message base is known. Existing carrier/send boundary checks remain in force. No permission, service credential, cloud endpoint or persistent message database change is introduced.
- Computer checks do not measure real Pixel frame times, storage contention or provider latency. See VALIDATION.md for tested behavior and remaining physical-device checks.

## 0.8.6 carrier attachment handling

- A system document picker provides access only to selected outgoing files; no broad photo/storage permission is added. Staged attachments and carrier PDUs stay in app-private storage. Canonical one-person recipient, selected active SIM, SMS/default-app access, current conversation and pending work are checked before carrier submission. Carrier size/type restrictions and bounded photo decoding apply. The UI never passes arbitrary network URLs to the MMS service.
- MMS is an explicit manual action. Selection pauses automatic replies for that conversation; pending AI output is invalidated. Stable request IDs, exact provider bindings and persistent outcomes prevent replay or blind retry. Sent requires a successful validated carrier response. Ambiguous sends remain unknown. No RCS, group sending, attachment timers or AI uploading of staged outgoing files is enabled by this feature.
- Incoming WAP push retains Android's signature-level broadcast protection. Private callback components bind opaque request tokens to durable provider records. Download/parser/part sizes are bounded; retries use the saved carrier notice, not a caller-supplied URL. Per-message status and recovery preserve failures for explicit user action. Protocol acknowledgments go through Android's MMS service and are not read receipts or messages to contacts.
- Received files opened externally use bounded private copies with read-only URI grants after an explicit tap. HTML, SVG, scripts and APK MIME types are not handed to a viewer. The local preview endpoint serves staged supported images only. Existing backups and app cleartext restrictions remain disabled; carrier transport is owned by Android.
- This is a scoped implementation review, not a claim of independently audited security or tested carrier interoperability. The existing third-party MMS parser, Android provider, device codecs and carrier service remain dependencies. See VALIDATION.md for actual build, policy, UI and installer evidence, and remaining physical-device checks.


## 0.8.4 media, foreground suggestions and approved examples

This targeted implementation review covers mixed SMS/MMS history, local attachment playback, review-only media analysis and selected writing examples. It adds no Android permission, exported component or database schema migration. Existing per-contact cloud opt-in, pairing, hosted model and sending authorization remain. **Suggest replies while chatting** defaults on and can request text or media suggestions while an already enabled conversation is open; it does not enable a contact, grant automatic-send permission or replace the owner's typed draft.

Local image/audio/video playback uses constrained numeric MMS part IDs with existing SMS access checks, allowed MIME types, bounded byte ranges, `no-store` and `nosniff`. The WebView still blocks remote navigation and network loads. Native media analysis checks the selected incoming MMS and confirmed recipient before and after capture. At most three resized, re-encoded JPEG images or sampled video frames are sent, with bounded captions/context, through the authenticated service. No remote image URL is accepted or fetched by that endpoint. Video interpretation excludes audio transcription and complete motion; missing or incomplete context prevents a reply suggestion. Image content, visible text and intent remain untrusted and may be misinterpreted. Media results are review-only and cannot schedule or send a reply. Historical analysis cannot impose a planning hold on a newer conversation.

Approved writing examples are selected per person from successfully sent manual final replies, with up to 12 distinct examples. Unreviewed automatic sends, unconfirmed/failed sends and the fixed Delay acknowledgment are excluded. These examples are sent only with history matching enabled, as bounded request data rather than instructions, personal facts or model training. **Forget these examples** advances the local selection boundary; it does not delete chat history, erase earlier provider records or prevent later manual replies from becoming examples. Existing private send records supply the examples; there is no additional chat archive or database migration.

The media endpoint shares the existing authentication, request deduplication, daily/rate/concurrency limits and no-response-storage configuration. Image-bearing requests have a 1 MiB limit; individual decoded JPEGs are limited to 150 KiB, 450 KiB total. No prompt/image content is written to relay logs or disk; provider retention still applies. Physical Pixel playback/codec, carrier and lifecycle verification remains pending. See **VALIDATION.md** for actual automated and deployment evidence; this section is not an independent security certification.

## 0.8.3 per-contact humor

Contact profiles add a strict integer humor level (0–4) and optional inside-joke text (2,000 UTF-16 characters). UI, bridge, storage and relay enforce the same bounds; invalid types and overlong text reject without silent truncation. Missing legacy fields use saved preferences or the Light/empty defaults. Database v13 preserves prior settings and send/action provenance. Changing preferences increments the existing profile revision and invalidates automatic work under the existing send lock.

The selected contact's notes travel in quoted request data, never trusted instructions, alongside the same contact's history. Each request and cache identity include the two fields; notes are not stored as server-side model memory. Cloud use follows the existing per-contact opt-in. Local quick suggestions remain generic. Fixed prompt guidance treats intensity as a ceiling, keeps inside jokes contextual and preserves serious-message, planning, stop, location and output safeguards. Model interpretation is not guaranteed; it is not a deterministic humor or privacy classifier. No permissions, exported components or credentials changed. See VALIDATION.md for current test and deployment evidence.


## 0.7.16 carrier delivery receipts

Delivery reports are separate from send approval, submission results and read receipts. Every newly submitted SMS part gets an explicit private delivery PendingIntent. Only these report callbacks are mutable so Android can supply the PDU and format; their component/action/URI stay fixed, and the job/part identity is read from that URI. Sending, alarm and notification PendingIntents retain their prior flags. The non-exported receiver rejects malformed identity and missing/invalid report data; it never treats a broadcast result code as delivery proof.

Only explicit GSM received status or CDMA received status confirms a part. Every part must confirm before the message shows Delivered. Interpreted reports and aggregate state are stored atomically, survive restarts and merge duplicate/out-of-order callbacks without regressing delivered evidence. Raw PDUs are not stored. Neither a missing report nor a delivery failure triggers a resend or grants send approval. The receipt adds no new sound or notification.

Late provider mutations and private receipt annotations bind the exact message URI, thread, address, body and recorded SMS date. Deleted/reused IDs cannot transfer an old receipt or submission result to a new message. Legacy jobs without that binding retain Queue outcomes but skip ambiguous provider writes. Visible older history refreshes only bounded, thread-scoped IDs. Version6 database changes preserve drafts, relationships, previous jobs and submission receipts; old jobs start with no delivery proof. See VALIDATION.md for tested behavior and remaining real-carrier checks.

RCS is not enabled: the installed app cannot access the privileged carrier RCS interface. The capability explanation does not request special privileges or change the default texting app.

## 0.7.15 local contacts lookup

The contact picker uses the existing READ_CONTACTS permission, with a dedicated Android permission request and App info fallback. Only local phone records are queried on a worker, using the provider’s encoded name/number filter, a bounded query and at most 80 sendable choices plus lookahead. Names, labeled phone numbers and row identifiers are returned to the packaged UI; photos and emails are not queried, and contact-search results are neither persisted nor sent to the drafting service. Permission is checked before querying, after reading, and immediately before bridge delivery.

Names and labels are escaped in the UI. Stale searches, revoked permissions and results arriving after navigation cannot restore a prior list or open a different conversation. The selected number is captured and controls are locked before pending draft writes finish. Opening uses the existing validated compose action and sender safeguards; contact search itself has no send action or additional network access. Real Android permission/provider behavior remains a device check; automated evidence is in VALIDATION.md.

## 0.7.12 explicit tap-to-send

At the user's request, tapping the composer send arrow is now approval to send that exact displayed reply immediately. There is no second native dialog for this direct action. Holding the arrow opens scheduling and suppresses the release click; delayed manual sends retain the native confirmation. This deliberately updates the historical approval-only flow described below.

The native `sendNow` action is restricted to the foreground packaged WebView and reuses the locked sender pipeline with a fixed zero delay. It validates message size, recipient/thread binding, current message, SMS permissions/default role, active SIM and pending/previous send state. It cannot accept a caller-supplied delay. Full and partial obscured touches are consumed by the Activity before they reach the WebView. No new permission or exported entry point is added.

The UI locks sending before flushing pending edits, preventing repeated activation while saves or sends are in flight. It clears the draft only for accepted `sending`/`sent` outcomes; errors, paused jobs and uncertain outcomes retain it. Duplicate/uncertain carrier submissions do not automatically retry. A late AI response cannot save another reply for a message with a submitted or uncertain send; that check is scoped to the same contact and message base. Current validation evidence and physical Pixel limitations are recorded in VALIDATION.md.

## 0.7.2 typing changes

Draft writes are batched, but the first automatic-timer edit is saved immediately and boundary actions flush the exact latest text. Native message checks remain fresh and query only the newest ID. The packaged WebView may finish saveDraft during activity pause; this action only stores an edit/cancels its automatic timer and retains current-message/active-send guards. Other bridge operations retain the foreground gate. No additional sending permission, schema, exported component or network capability is added. See VALIDATION.md for asynchronous draft-save regressions and remaining real-device checks.

## 0.7.1 contact-specific instant sending

At the user's request, **Send instantly** adds a zero-delay form of the existing per-person standing authorization. It is never selected by migration. Existing delayed modes and manual confirmation remain unchanged. Instant mode sends a newly generated reply after notification posting, without per-message review or an intervention window. Saving a profile alone cannot send an old draft. Exact-alarm access is required only for delayed sends; instant mode uses the existing guarded carrier dispatch path directly.

The same global/person opt-ins, cloud/profile revisions, current incoming message and draft, single-recipient and active-SIM checks apply. Notification posting failure blocks sending. The notification retains the original/reply and silently updates the carrier outcome. Automated tests do not prove on-device notification or carrier behavior; see VALIDATION.md.

## 0.7.0 change to the authorization model

The historical review below covers **0.6.2**, whose sends all required individual approval. Version **0.7.0** deliberately adds the user-requested, per-person **Send after timer** permission. Newly generated OpenAI replies can now send without review after the saved delay. This is a change in behavior, not a claim that 0.6.2’s approval-only audit certifies the new feature.

The new mode defaults off for existing and new profiles. Automatic jobs record standing authorization separately from manual approval and recheck the profile/configuration revisions, current message and draft, one-person recipient, selected SIM, SMS role, permissions, notifications and timing before dispatch. Alerts post before alarms are armed; interrupted alert reservations pause. Editing, redrafting, changed profiles, disconnecting, global pauses and newer messages stop the affected automatic jobs. In-flight generations are rejected after global pauses. The notification action only cancels; its receiver is nonexported and the pending intent immutable.

Draft/timer notifications now show incoming text and suggested replies on the lock screen by default, as requested. This exposes content to anyone able to view those notifications, subject to Android’s privacy controls. Settings can disable these previews. Notifications use BigTextStyle and the default system notification sound, respecting Android size, channel and Do Not Disturb limits. No permission was added.

The original non-debuggable release, dependency patches, restricted WebView, server-only API key and guarded manual approval dialog remain. See **VALIDATION.md** for the current automated evidence and untested Pixel/carrier behavior. No new live AI requests, real SMS, or production service changes were made for this update.

---

## Result

The review found a debuggable installer, five published vulnerability advisories matching three bundled libraries, and an opportunity to protect send approval against obscured touches. These are addressed in **0.6.2 (version code 9)**. The changes are in the new installer; they are not applied to the Pixel until that update is installed.

No approval bypass or credential disclosure was found in the reviewed code and tests. This is a scoped code and configuration review, not an independent penetration test or a guarantee that the app is safe. Real-phone carrier, scheduling, overlay and storage checks remain necessary before relying on it as the main messenger.

## Scope and method

Reviewed the Android manifest and merged release manifest, Java source, packaged WebView assets, database and backup rules, authentication and HTTPS clients, AI context selection, SMS approval/dispatch/recovery, Node relay, packaging, signing compatibility and Railway service configuration. Inspected logging behavior in the bundled MMS library. Queried OSV with public Maven package names/versions; no private code or conversations were uploaded.

The baseline was the 0.6.1 installer. The reviewed live Railway deployment was `1daa2005-209f-4bc8-9e26-b6b6e26f74c0`, with one replica and variable names checked without reading the OpenAI key. This audit made no production configuration changes, generated no AI replies and sent no SMS. The existing phone credential was used privately for read-only service checks and was not printed or put in the report.

## Findings and changes

### 1. Debuggable installer — medium local-access risk; fixed

The 0.6.1 manifest enabled `android:debuggable`, and `tools/package.py` distributed the debug APK. Debugging increases exposure when someone has suitable local/device debugging access; this was not a demonstrated remote exploit. Android recommends disabling it in shipped apps. [Android guidance](https://developer.android.com/privacy-and-security/risks/android-debuggable)

`app/build.gradle` now builds a non-debuggable release, the package tool selects that release, and `MainActivity` explicitly disables WebView debugging. The compiled 0.6.2 manifest confirms debugging and test-only mode are off. Backup and cleartext traffic remain disabled.

The preserved private development signing key is retained solely so the existing personal installation can update without losing pairing or profiles. The APK is non-debuggable despite retaining that certificate. The signing key is excluded from archives and its file is owner-readable only. Production distribution should use a planned production signing/update strategy; this review did not migrate signing identities.

### 2. Outdated transitive libraries — dependency findings; fixed versions bundled

Five advisories matched versions brought in by the optional local AI libraries:

| Baseline component | Advisory | Issue described by the advisory | Version in 0.6.2 |
| --- | --- | --- | --- |
| Guava 31.0.1-jre | [CVE-2020-8908](https://osv.dev/vulnerability/GHSA-5mg8-w23w-74h3) | Temporary-directory information disclosure | 33.4.8-android |
| Guava 31.0.1-jre | [CVE-2023-2976](https://osv.dev/vulnerability/GHSA-7g45-4rm6-3mm3) | Temporary-file information disclosure under affected platform conditions | 33.4.8-android |
| OkHttp 3.0.0 | [CVE-2021-0341](https://osv.dev/vulnerability/GHSA-3cqm-mf7h-prrj) | Improper hostname/certificate verification | 4.12.0 |
| OkHttp 3.0.0 | [CVE-2016-2402](https://osv.dev/vulnerability/GHSA-4hc2-jh7r-wrc3) | Certificate-pinning bypass | 4.12.0 |
| Okio 1.6.0 | [CVE-2023-3635](https://osv.dev/vulnerability/GHSA-w33c-445m-f8w7) | Malformed GZIP can cause denial of service | 3.6.0 |

These are confirmed affected-version matches, **not five proven exploits against Reply Pilot**. App-owned code does not call the implicated Guava temporary-file APIs, and the OpenAI relay connection uses Android's `HttpsURLConnection`, not the old OkHttp client. Reachability inside bundled SDKs was not established. Android version and sandbox restrictions also affect applicability.

Explicit dependency overrides remove those versions. All **74 resolved release runtime artifacts** were checked again against OSV: **zero advisory matches at the time of the scan**. This does not cover unknown vulnerabilities, unindexed packages, native binaries or the server operating system. Compilation/tests pass; the optional local AI engines still need compatibility checks on the Pixel.

### 3. Send approval did not explicitly reject obscured touches — defense in depth; added

The original native confirmation dialog had no explicit check for full or partial window occlusion. Another installed app with overlay capabilities could potentially obscure what the user is approving; an exploit was not reproduced. Android documents partial-occlusion checks as an additional mitigation. [Android tapjacking guidance](https://developer.android.com/privacy-and-security/risks/tapjacking)

The new `ApprovalDialog` consumes touch events flagged as fully or partially obscured. The exact recipient, text and delay remain visible in the native confirmation, and only its positive acceptance callback creates a send job. This adds no permissions. Benign overlays may also prevent approval until dismissed. Physical-device overlay and accessibility behavior were not tested.

## Controls reviewed and exercised

- **Approval and timers:** exact approved text/recipient/SIM are stored locally; the native layer checks recipient/thread consistency, authorization, SIM, conversation freshness and delay bounds. Cancellation applies before dispatch. A new message, reboot, update, clock change or lost SMS role pauses queued sends. Uncertain carrier outcomes do not automatically retry.
- **Other apps:** incoming SMS/MMS components require Android broadcast permissions. Internal send receivers are not exported. SENDTO and respond-via-message entry points create/open drafts and do not directly send. The merged dependency profile-install receiver requires the privileged DUMP permission.
- **WebView:** only packaged assets and constrained local MMS image paths are served. Remote navigation, network loads, file/content access and mixed content are blocked. The content security policy restricts scripts to packaged files. Hostile contact names, SMS/MMS text, profiles, drafts, queue notes and AI output rendered as inert text in browser tests, with no external requests or send/save actions.
- **Credentials and storage:** the phone credential uses Android Keystore-backed encryption; the OpenAI key remains on the server. Backups are disabled. The local message/profile database relies on Android app-private storage and device protections; it does not have an additional app-level encrypted database layer.
- **Cloud access:** per-person opt-in and bounded, conversation-specific context are enforced locally; stale generation results are discarded. The relay authenticates with the phone token, rejects browser Origins, validates and limits input, strips unknown request fields, fixes the provider endpoint/model server-side, disables response storage and supplies no tools capable of sending SMS.
- **Service containment:** generic errors avoid raw provider details; app code does not log prompts/replies/tokens. Concurrency, per-minute and daily request limits are present. The container configuration uses a non-root user. Runtime image vulnerabilities and infrastructure administrators' access were not independently assessed.

## Verification

| Check | Result |
| --- | --- |
| Release build | Passed |
| Android unit tests | 39 passed; no failures/errors |
| Android lint | 0 errors, 8 warnings |
| Mocked relay tests | 14 passed |
| Browser regression/security scripts | 8 passed; synthetic data and simulated native bridge |
| OSV release dependency scan | 74 artifacts; 0 matching advisories |
| Live service GET checks | 6 passed: public status, missing token, incorrect token, valid token, forbidden browser Origin, absent send route |
| Live TLS | Normal certificate and hostname validation passed; no bypass |
| Installer | Signature matches 0.6.1, version increases, permissions unchanged, 16 KB ZIP alignment passes |
| Packaged assets/secrets | Assets match source; known phone token absent; API-key pattern scan found no matches |

The lint warnings concern newer available build/dependency versions, ARM-only support, intentionally enabled JavaScript for packaged UI, and required telephony hardware. Newer-version warnings are not themselves vulnerability matches. Secret-pattern matching is heuristic; it cannot prove all possible secrets are absent.

Machine-readable evidence is under `security-audit/`: the baseline advisory matches and details, final runtime inventory and scan, live checks, browser results and installer checks. Android test/lint reports remain under `app/build/`. The release APK SHA-256 is `4326e539ff529205f42aaced3d2f387999dfb6b829727896656790a608ac3587`.

## Residual risks and follow-ups

1. **Phone validation remains the main gap.** Test receive/send, exact recipient and text, decline/cancel, lock-screen timer, newer-message pause, reboot/update/role loss, carrier failure, MMS and the new overlay guard on the actual Pixel. Automated tests do not establish carrier delivery or Android lifecycle correctness. See `VALIDATION.md`.
2. **Cloud privacy remains a deliberate tradeoff.** Enabled conversation text, relationship notes and samples go through Railway to OpenAI. `store:false` is not a zero-retention guarantee. API data is not used for training by default, but standard abuse-monitoring retention can apply. [OpenAI data controls](https://developers.openai.com/api/docs/guides/your-data)
3. **A stolen pairing token permits AI usage.** It is a bearer credential without automatic expiry. The relay exposes no inbox-reading or SMS-sending route, but misuse can incur charges. Rotation is documented in `relay/README.md`. In-memory quotas reset on service restart and are not durable billing caps; account billing controls were not audited.
4. **Retention and device compromise:** saved profiles/drafts/jobs remain until removed through available app/data controls; there is no automatic retention policy. A compromised/unlocked privileged device or compromised hosting account is outside this review's protection claims.
5. **Low-priority server memory hardening:** `relay/core.mjs` checks provider response size after `response.text()` has buffered it. The fixed HTTPS provider, output-token cap, timeout and two-call concurrency limit constrain exposure, but streaming byte limits would better contain an unexpectedly oversized provider response. No exhaustion was demonstrated and this relay behavior was not changed.
6. **AI output still requires judgment.** Incoming text or samples can influence the model, which may invent commitments or repeat private context. Prompt instructions are not a security boundary. Keep reviewing the exact draft before acceptance.

No exhaustive fuzzing, native-library reverse engineering, container/OS scan, physical-device penetration test, malware assessment of the user's phone, or independent third-party review was performed. No blanket “secure” certification is asserted.

## Install the fixes

Transfer `dist/Reply-Pilot-0.6.2-install.zip` to the Pixel, extract it, and open `Reply-Pilot-0.6.2.apk`. Install over the existing app without uninstalling. Confirm Version 0.6.2 in Settings; pairing and profiles should remain. Existing accepted timers pause for fresh approval after updating. The Railway service does not need a deployment for these Android fixes.


## 0.8.0 scoped implementation review — 2026-09-24

This is an implementation review, not an independent certification. New optional coarse/precise/background permissions default to no location collection. The location master switch, Android grants and per-contact opt-in are independent gates. Raw coordinates/home address are not part of the native WebView snapshot or OpenAI draft payload; disclosed Android geocoding and the fixed HTTPS OpenStreetMap endpoint receive lookup inputs. Only the latest fix plus optional saved home is retained, with backups disabled. The public map endpoint is for this personal preview; it is not a production-scale map contract.

Send provenance includes location revision and expiry. Global/home/fix revision writes serialize with final carrier checks under SEND_LOCK → location lock, preventing app-side revocation between the final check and submission; no location network call holds the send lock. OS permission changes or carrier acceptance cannot be recalled atomically, so no absolute guarantee is claimed. Freshness rejects boot/clock mismatch and expired evidence; unedited AI location timers are held when the location or consent changes. The relay only allows bounded coarse labels, excludes location context from unrelated requests, and holds missing/stale location questions.

Media navigation uses actual provider IDs and same-thread cursors; read-only historical browsing cannot become a send base. About me fields are bounded data and cannot override planning, history, loop or output rules. An independent teammate review identified the app-side location mutation race, which was fixed before the final Android build. Automated tests use fictional data; no live phone location, geocoder, map lookup or SMS was exercised.


## 0.8.1 targeted change review

Girlfriend Autopilot introduces no new permission, exported component, public endpoint, database schema or credential. Engagement is validated on phone and relay. Pause preferences retain only a thread ID, SMS watermark and boolean; incoming text is not copied into that store. Per-contact consent, recent-history eligibility, planning handoff, private location gating, message-burst checks, repeat suppression and the five-consecutive-automatic-submission cap remain. Bedtime detection executes before automatic generation and pause state is rechecked at dispatch. Explicit Resume neither re-sends nor re-drafts the prior bedtime message. UI-controlled pause changes and receipt insertion share the existing carrier-send lock; preference failures stop automatic sending. User-entered context remains bounded data, never trusted instructions.

This is a scoped code and automated-test review. Relationship judgment and unfamiliar bedtime wording still depend partly on model behavior; physical Android/carrier behavior has not been tested in this task. An SMS already handed to the carrier cannot be recalled.


## 0.8.2 targeted notification action review

New Joke/Delay actions are opaque UUIDs bound to the current SMS identity, conversation, profile revision and incoming burst; Joke also binds the existing AI connection. Tokens are stored privately in SQLite, accepted once through bound updates and never inferred from received SMS content. Notifications use explicit immutable private receiver intents, action authentication and a runtime unlocked-device check. The background Joke JobService is private and protected by BIND_JOB_SERVICE. No new Android permission or credential was introduced.

Joke is a review-only regeneration, not send approval. It may reconsider literal incoming keywords but retains output, privacy and contextual handoffs, current-token/message checks and edited-draft protection. Delay approval is limited to a fixed displayed SMS. Job creation, deferral marking and token linkage are transactional before carrier submission; final recipient/base/fingerprint/burst checks protect against newer incoming messages. A deferral cannot clear planning holds, reset the automatic reply count or become a personal style example. It never inherits location metadata. Once an SMS job exists, an action cannot be reoffered or retried automatically, including unknown outcomes.

Read-only review found a queued-action recovery gap and stale progress feedback; both were fixed before the final build. Startup/resume/recovery drains only queued Joke work, leaving active work protected and interrupted work consumed. Automated policy, SQL, mocked HTTP and synthetic live model checks passed. This remains a scoped code/test review; physical Android notifications, disk/process failures and carrier behavior require on-device validation.


## 0.9.4 training and meaning-context review

Practice is a separate authenticated inference operation with no SMS or scheduling tool. Only explicit owner practice answers become examples; simulated contact messages are labeled as such and cannot count as actual history for automation eligibility. Lessons and meanings are bounded per contact in the app-private SQLite database (backups remain disabled). Native contact-address scope and received-message identity checks prevent stale or cross-contact explanation reuse. New guidance invalidates prior generation/automatic timers; it never grants send consent or clears planning holds. The long-press editor sends a native-generated message fingerprint, which must still match before persistence.

The relay treats all lessons, notes, history and scenarios as untrusted data and keeps fixed instructions separate. Retired About me data is excluded from provider input. Practice shares existing auth, rate and concurrency limits. Repeated accepted native turn tokens cannot create a second lesson; only failed practice request entries are evicted for retries. Native cleanup now matches relay stripping of invisible controls so a blank hidden string cannot poison future requests. New tables are not included in FileProvider paths. No new Android permissions or credentials were introduced.

Validation included 451 native unit tests, 120 mocked relay tests, nine SQLite checks, nine browser suites, installer inspection and a fictional live service check. This is a focused implementation review, not an independent audit or real-device carrier test. Saved text remains sensitive local application data; enabled cloud requests share selected context with the configured service and OpenAI, as stated in the UI.
