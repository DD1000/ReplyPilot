# Install Reply Pilot 0.11.1

## Build on the Mac

Double-click **Build Reply Pilot.command** in the project folder. It uses Android Studio's built-in Java and the Android SDK in `~/Library/Android/sdk`, runs the unit tests, and saves `dist/Reply-Pilot-0.11.1.apk`. The build log is saved as `build-log.txt`. The signing key in `.local-signing/` must stay in place; without it the phone rejects the update.

## Update the Pixel

Transfer **Reply-Pilot-0.11.1.apk** from `dist/` with Quick Share, then open it in Files by Google and install it as an update over Reply Pilot; **do not uninstall**. Updating in place keeps your pairing, profiles and drafts. Check Reply Pilot → Settings for **Version 0.11.1** (version code 65).

## New in 0.11.1

- Sending uses the phone's only active SIM even when its ID changed, instead of stopping with “Select an active SIM in Settings.”
- An empty Sending SIM menu explains whether Phone access is missing or Android reports no active SIM, with buttons to fix Phone access.
- Choosing a sending SIM saves immediately.

## Previous release: 0.11.0

- Reply setup now has **Off** and **Autopilot**. The compact **Timer** defaults to **Instant**, with **1 minute** and **5 minutes**. Tap the composer arrow to send now; hold it for the same timer choices.
- Train Pilot, Reply needs you actions, conversation-style selectors, girlfriend mode and the old send-after/sleep/range controls are removed. Relationship Dynamic, Important Details, approved writing examples and optional chat-log import remain.
- Autopilot responds once to each verified new incoming message or collected burst, including ordinary acknowledgments. It never agrees to plans or invents availability: requests needing your decision receive a brief deferral and a **Chat needs attention** alert after submission. Actual incoming attachments get a brief acknowledgment and attention alert; that acknowledgment does not claim to understand unseen media.
- The insufficient-history warning disappears once the contact qualifies. Turning a contact Off does not require a successful history scan. Contacts that were Off stay Off; only previously enabled automatic sending carries over.
- Initial preparation sends **all available sent/received SMS/MMS text** through your connected service to OpenAI, in bounded per-conversation batches. The phone saves encrypted checkpoints and per-contact summaries; those summaries plus recent context guide later replies. This is contextual memory, not permanent model training. Private RCS history and unavailable attachment content are not part of this analysis.
- AI conversation replies wait for initial analysis to finish. You can keep sending manually. Settings shows progress and Retry; network failures resume completed work. A large history may take substantial time and use the existing service's 200-request daily allowance; a limit error pauses analysis until you can retry. No silent partial analysis is reported as complete.
- Preparing history does not send messages or enable contacts. Manual sending still stops AI work for the current incoming turn. Updating pauses old queued automatic work and retains your pairing, contact guidance and owner-written drafts.

Install over the existing app without clearing storage. No new permission or API key is required. The hosted service has been updated for this release; other self-hosted installations must update their relay too. “Instant” means no extra timer after the reply is ready; incoming burst collection, generation and Android/carrier scheduling still take time.

## Previous release: 0.10.4

- A non-empty message keeps the Send button available during AI work, conversation loading and pending replies. Manual sending verifies the current recipient and Android sending requirements directly; it does not wait for an AI result or full inbox/history refresh.
- Sending manually takes over the current conversation turn: pending automatic replies and planning deferrals stop, in-flight AI results are discarded, and automatic suggestions stay suppressed for the answered message. A genuinely new incoming message can start the next automatic turn.
- Manual follow-up messages can be submitted without waiting for delivery confirmation of an earlier send. Repeated taps share one request identity to prevent duplicate submission. New text typed during sending is kept.
- Manual timer confirmations retain their existing checks. A message already handed to the carrier cannot be recalled.
- Install over the existing app to retain pairing and preferences. No new permission, API key or service deployment is needed.

## Previous release: 0.10.3

- Recent conversations update through a separate, bounded provider reader when messages arrive or sending changes. The inbox no longer waits for a full history refresh or AI drafting to show recent activity.
- Conversations sort by the latest message time in both directions, using normalized SMS/MMS timestamps. Pinned conversations stay first and sort by recent activity within their group.
- Fast updates merge into the existing inbox; they do not discard older conversations. A full refresh still reconciles deleted messages and chats. Older in-flight results cannot overwrite a newer live update.
- Carrier receipt processing uses separate serial workers so saving incoming SMS/MMS and completing MMS sends cannot queue behind the full inbox. MMS download/retry/recovery stays serialized. A committed incoming SMS publishes a refresh before notification/contact/AI work.
- Updating preserves saved chats, pairing and preferences. No new permission or hosted-service deployment is needed.

## Previous release: 0.10.2

- **Text-only MMS behaves as text.** “Understand & reply” and attachment-interpretation panels appear only for actual attachment parts. MMS presentation metadata no longer appears as a file. The small MMS label still describes how the message was transported.
- **Draft reply works for a verified incoming text-only MMS**, including chats with no SMS history. Foreground suggestions use the existing OpenAI text service and recent mixed SMS/MMS context. These drafts wait for your approval. Incomplete or attachment-containing unanswered messages, plans, personal-location questions and changed conversations pause for review.
- Saved generated MMS replies are bound to the exact source message, text, timestamp and recipient. A new incoming message cannot resurrect an old generated reply just because the SMS position stayed the same. Owner edits remain yours.
- **Inbox taps survive background refreshes.** The pressed chat row stays attached until its touch/click finishes, and a cancelled pin gesture cannot consume the next fresh tap. Chat contents open immediately while current history updates separately. Long-press pin controls still work.
- Install as an update without uninstalling or clearing storage. The storage upgrade preserves existing drafts and preferences. No new permission, API key, phone pairing or hosted-service update is needed.

## Previous release: 0.10.1

- A confirmed delivery clears a stuck “Sending reply…” job, including saved jobs from earlier versions. Late callbacks and older screen refreshes cannot undo confirmed delivery.
- Manual send, draft saves and carrier receipts use separate work queues from inbox/history loading. The composer clears after carrier submission begins and remains available for typing your next message. The next Send stays guarded until the previous attempt settles.
- Draft reply shows **Drafting…** immediately. It waits only for that chat’s draft save and displays the generated result without reloading the whole history. Stale-message and connection errors are shown; your typed wording is kept on failure.
- Message taps no longer open **Explain meaning**. Long press uses Android text selection. Copy/Cut finish their native toolbar; outside-tap dismissal and complete gesture cancellation avoid leaving the WebView stuck. This release adds Android’s normal `HIDE_OVERLAY_WINDOWS` permission to hide third-party overlays while using the app; it does not add a runtime permission prompt.
- Install over your existing app, without uninstalling or clearing storage. Pairing and profiles stay saved. No hosted-service change or new API key is needed for this update. Carrier speed, cloud generation latency and physical Pixel keyboard/selection behavior still require device testing.

## Previous release: 0.10.0

- The former tap-to-explain control has been removed in 0.10.1. Hold message text to select and copy it.
- Text fields stay connected through background updates, preserving the keyboard and cursor. A deliberate tap can reopen the keyboard. Custom timer fields have more space.
- Reply setup now uses **Relationship Dynamic** (1,500 characters) and **Important Details** (2,000 characters). Tone, relationship-type, humor and inside-joke controls are removed, including Myself. Your sent, approved and practice replies guide your voice. Retired data is retained privately but is not active AI guidance in this version.
- **Girlfriend Autopilot** fixes its conversation style while allowing its send delay to change. Goodnight/stop, burst waiting, history and repeated-reply protections still apply.
- **When they make plans → Delay answer** is optional. With automatic sending enabled, it can send one neutral “I'll get back to you on that.” acknowledgment, then keeps plans paused for your input. It never agrees to plans. The default is **Ask me first**. New texts, edits, preference changes, stale work or missing access can cancel the acknowledgment.
- Fixed timer menus include **1 sec · test**. This starts after a reply is ready; incoming-message quiet time and AI generation still take time. Custom and random-range values remain whole minutes.
- Gemini Nano, local quick replies and their model downloads are removed. AI features use your existing hosted OpenAI connection.

Install the update over your current app. **Do not uninstall or clear storage.** Your existing phone pairing is retained. Your existing hosted service has been updated for the full new context fields. Other self-hosted installations need the bundled 0.10.0 relay update. No new phone permission or API key is needed.

Earlier release notes below describe their original interfaces; the 0.10.0 changes above supersede retired controls.

## New in 0.9.13

**Chats with older messages or MMS text can qualify correctly for automatic replies.** The old check looked at only the newest 50 SMS, so a rich chat could show just a few usable messages. The new check reads both SMS and MMS text, including older examples, directly from the phone's message provider. It stops once enough distinct examples are found, so “At least” is a lower bound, not your chat's total size.

Open the chat → **Reply setup → Recheck history**. When it shows enough history, choose your preferred automatic mode and save. Rechecking does not enable or send anything. Settings → Prepare replies automatically must also be enabled for your selected automatic modes to run.

The minimum remains 20 distinct messages, including 5 from you and 5 from the other person. Repeated text, attachment placeholders and known automatically sent replies do not add owner-writing examples. An unusually large or unreadable history can show an incomplete/retryable check; a labeled chat log is another way to provide examples. Private RCS-only history is still unavailable through Android's SMS/MMS provider.

Install over the current app. **Do not clear storage or uninstall.** No server update, new API key or new permission is needed. This update also includes the share-menu support from 0.9.12.

## New in 0.9.12

**Reply Pilot appears in Android’s share menu.** From another app, tap Share → Reply Pilot (expand the full app list if needed). Choose a contact, recent conversation, or phone number. Tap **Add to draft**, review the text and attachments, then tap the send arrow. Existing draft text is kept and the share is appended. Nothing sends automatically from this flow.

Text and links are limited to 1,600 characters including an existing draft. Share up to six supported photos, videos, audio files or contact cards at a time; normal MMS file/carrier limits still apply. Android must grant access to the shared files. If the source app’s file access expires, share it again. Unsupported formats show an error rather than being sent as broken attachments. No new permission or service update is required.

Install over the existing app. The old installed version will not gain the share-menu entry until updated. The Android share sheet and carrier delivery still need testing on the physical Pixel.

## New in 0.9.11

**History loading no longer waits behind the full inbox scan.** Live chat and older-page reads have their own bounded workers. A late saved page cannot push a freshly loaded conversation back into preview mode. When a read takes unusually long, Retry becomes available and preserves your typing.

**Saved history resumes where it failed.** Retry no longer jumps back to the newest saved page. Unchanged background scans keep the same snapshot; a genuinely updated archive is retried once at the same message boundary. The first rebuild after clearing storage shows “Saving your chat history on this phone…” rather than a false permission error. It updates when the rebuild completes. Already-read conversations avoid empty provider writes that can repeatedly restart sync.

Install over the existing app; do not clear storage. Open a chat and scroll upward to check older messages. Settings → Saved chat history shows the initial rebuild status. Only SMS/MMS exposed by Android are available; this update cannot recover private RCS history or erased app-private profiles/pairing. No service update or new permission is required.

## New in 0.9.10

**Default-app setup no longer waits for chat history.** Android's live role and read/send permissions update the setup controls immediately. A chooser with no result stops waiting after 30 seconds; returning from Android Settings checks status separately. A slow or missing response does not count as approval or denial.

If setup is stuck, use **Open Android default apps → SMS app → Reply Pilot**, then return and tap **Check status**. You can also go directly to **Pixel Settings → Apps → Default apps → SMS app → Reply Pilot**. If Android explicitly blocks restricted settings, use **Settings → Apps → Reply Pilot → ⋮ → Allow restricted settings** before trying again. Reply Pilot cannot override Android's decision or device restrictions.

Install over the existing app. Clearing app storage removes pairing, preferences, drafts, practice examples and saved caches; this update cannot recover those cleared app records. After reconnecting and granting access, available SMS/MMS history can be read again from Android. Do not clear storage again to apply this fix.

## New in 0.9.9

**History gaps are repaired.** Reopening a chat keeps messages already loaded; gaps between older saved history and the newest phone messages fill automatically. A failed older-page load retries the same range. Text stored inside MMS files is now read correctly, and an unreadable text file no longer stops the entire history archive.

Open **Settings → Saved chat history** to see saved conversation/message counts and sync status. Use **Refresh saved history** to rescan. Leave the app open for the initial full sync; large histories can take time. This covers SMS/MMS available through Android. Messages stored privately inside another app may not be accessible, so this cannot guarantee an exact copy of all Google Messages history.

**Train Pilot now reads SMS and MMS text together.** It also includes an older incoming message if the newest 50 messages are all yours. There is no age requirement; the old recent-text warning was misleading. Unreadable individual MMS text is skipped without losing other usable history. The practice error screen has one retry action, with more space between controls.

Update over the existing app; no service change, new permission or re-pairing is needed.

## New in 0.9.8

**Contact photos appear beside conversations in Messages, including pinned chats and the saved list shown on launch.** The first rows load pictures immediately instead of waiting for lazy image loading. Photos come from your phone’s saved contacts; allow Contacts access under Settings → Phone setup. People without a saved photo show initials.

Update over the existing app. No new permission, pairing or service update is needed.

## New in 0.9.7

**Train Pilot is now above the text field inside the reply panel.** It opens while chat history is still updating and no longer waits behind other draft or media requests. If you close and reopen a practice session while a turn is being prepared, its progress updates automatically. The online AI still needs time to create each scenario or next turn. Opening the panel alone never starts an AI request.

Update over the existing app; keep your pairing, chats and practice examples. No service deployment or new permission is needed.

## New in 0.9.6

The **Media** tab now shows messages containing actual attachments. Text-only MMS stay in the conversation, and no longer take up spaces in the gallery. Photos, video, audio, contact cards and other files keep their captions and **Take me there** button. Pending MMS downloads remain available in their chat until the attachment arrives.

Install this update over the existing app. Your messages, cached history, theme, pairing and reply settings stay saved. No service update or new permission is needed.

## New in 0.9.5

**Reply setup is always available at the top right of a chat.** Open it immediately, even while messages refresh. You can edit the settings while the app checks the contact; Save becomes available after that short check. Settings for group conversations remain informational because group replies are unsupported.

**Saved contact pictures appear automatically.** Allow Contacts in Settings → Phone setup. The app uses photos already stored in your Android contacts; otherwise it keeps the person's initials. It does not retrieve private social-media profile pictures.

**The cache now covers all available conversations and message history.** Open the updated app with SMS access and let the first background sync finish. A large inbox can take longer on that first pass. Subsequent openings show encrypted saved history while fresh messages load, and scrolling retrieves older saved pages. Photos, audio and video files stay in the phone's message store and load when viewed; their chat entries are cached. This uses more phone storage and does not upload the archive to AI or enable automatic replies.

Install this update over your existing app. Your pairing and settings stay in place; no service update is needed.

## New in 0.9.4

Open a chat → **Train Pilot** above the text field → **Start practice**. The agent creates a simulated scenario using that person’s recent texts. Reply the way you would normally text them. Practice replies are remembered for that contact and guide later drafts; none are sent as SMS. You can forget practice examples from the practice screen.

Press and hold a received message → **Explain meaning**. Add context about sarcasm, a reference or what the person intended. Save, edit or remove the explanation there. Meaning notes do not authorize automatic replies or clear planning holds.

The About me page has been removed. **Myself (beta)** now draws on sent, approved and practice examples. These are request context, not permanent model training. Starting practice shares selected recent text and saved guidance with your existing private service and OpenAI.

## New in 0.9.3

The launcher icon now uses your coral R/reply-arrow image on purple. Android themed icons use the same shape in your phone’s selected colors.

## New in 0.9.2

Theme taps change the color and selected indicator immediately. Rapid taps save your latest choice without waiting through every intermediate selection.

Conversation previews now show actual SMS/MMS text when present, instead of always showing “Media message.” A photo or other attachment without text still gets a media label.

**Let messages finish loading once after updating.** The app rebuilds corrected inbox summaries and quietly saves up to30 recent message texts from each of your10 most recent chats. Later launches can display those histories immediately while fresh messages load. This cache is encrypted on the phone; attachment-only messages use placeholders and long cached texts may be shortened until live history arrives. It does not upload history or enable AI for anyone. You can type during refresh; send/AI controls wait for current message details.

## New in 0.9.1

Chat rows no longer have three-dot buttons. Press and hold a chat to pin or unpin it.

## New in 0.9.0

Your selected theme appears immediately on launch. Pinning or unpinning a conversation updates its position right away; if saving fails, the app restores the saved position and offers Retry.

In **Settings → Location replies → Home**, tap **Use current place** after allowing precise location and enabling location replies. Confirm **Is this your home?**, or use **Next address** / **Previous** to review up to ten nearby addresses. Tap **Yes, save this address** to fill and save the field. **Enter address manually** is always available; include the street number, city and postal code, plus your apartment/unit if needed. Searching and canceling do not change your saved home.

The phone needs internet for address lookup. GPS and map records can be incomplete or place you between neighboring buildings, so check the full address. A fresh fix within 50 metres of reported accuracy is required; results expire after two minutes. Android's geocoder and OpenStreetMap receive the lookup location, while the saved address is not included in AI location context. This does not grant location replies to additional contacts.

## New in 0.8.9

**Link previews in chats.** Open a conversation containing an HTTP or HTTPS link. Tap the underlined link or its card to open the original destination in the browser or a supported installed app. The first link in each message can show the public page’s title, website, short description and thumbnail. Multiple links remain separately tappable. The same behavior applies to MMS captions and text in historical media conversations.

Previews load quietly for visible messages after current chat details arrive. They do not block the conversation or typing. An unavailable/private/login-only page, an offline connection or a website that blocks previews leaves a tappable domain card. HTTP links can be opened but are not fetched automatically. Videos are not played inside preview cards.

**Settings → Link previews** starts on and can be turned off, then saved with your other preferences. Previews connect directly from your phone to the linked website and any public image host it names; those hosts can see the request and your IP address. The surrounding conversation, contact profile and API key are not sent for this feature. Previews use no OpenAI request and do not sign into Facebook or Instagram. Private posts may therefore provide no thumbnail or details. Turning previews off leaves ordinary link tapping available.

## New in 0.8.8

**Recent chats ready on launch.** Open the app once after updating and let your conversation list appear. That successful load saves the 20 most recent chats, with names, times and short previews, encrypted on this phone. Later launches show that list while it quietly refreshes. The first launch after installing this update must build the saved list; it cannot show a snapshot that has not been saved yet.

This is a preview of the last successful inbox read, so new or deleted messages may briefly differ until the refresh finishes. A failed refresh keeps your saved list and offers Retry. Opening a saved chat lets you type while the app checks current message details before enabling send and AI actions. Losing message/default-app access hides and clears the saved preview; losing contact access removes saved names.

## New in 0.8.7

**Faster conversations.** Tap a person to open the chat immediately with the history already available. Recent and pinned chats prepare in the background while you use the app. A first visit can show the inbox's latest message while the rest arrives; it no longer replaces the whole chat with a loading screen. You can start typing immediately, and the app checks the current conversation before enabling send or AI actions. Draft saves continue while you switch screens.

Preloading keeps a limited set of recent history in phone memory. It does not download every attachment or retain an extra chat archive. Older messages remain available as you scroll. It does not mark unseen chats read or call the AI. History refresh failures show a retry option; a cached preview is never permission to send.

## New in 0.8.6

**Send attachments.** Open an individual conversation and tap the paperclip beside the reply field. Android Files lets you choose one photo, GIF, short video, audio file or contact card at a time. You can add up to six supported files, remove them, and write an optional caption. Tap the normal send arrow to send the MMS now. Holding the arrow does not schedule attachments; timers remain for text-only messages. Your selected files stay on your phone and are not uploaded to the AI service merely because you attach them.

The composer shows the sending SIM's carrier limit. Photos are resized and re-encoded; video/audio/GIF files must already fit. Long videos and arbitrary document formats are not supported for outgoing MMS. Choose an active SIM in Settings and keep mobile data available. The carrier may charge for MMS and may require mobile data even when Wi-Fi is connected.

**Receive attachments.** Incoming MMS downloads through Android's carrier service. The message appears in its conversation with download status. If mobile data, the SIM or carrier settings prevent it from downloading, use Download/Retry on that message after addressing the displayed issue. Supported images, video and audio appear inline. Other supported received files offer Open attachment to choose an Android viewer.

**Sending results.** Sending means a result is pending. Sent means the carrier returned a successful MMS response; it does not confirm delivery or reading. Failed keeps the caption and attachments for a new explicit attempt. An unknown result stays blocked to avoid duplicate messages: check with the recipient before discarding the selection. Re-adding the exact same files and caption in the same conversation state does not authorize a duplicate send. Background AI suggestions and automatic replies pause while attachments are selected. Native carrier behavior, picker lifecycle and codecs still need testing on the phone; first try a small photo in each direction with someone who agrees to help.

## New in 0.8.5

All ten color themes now use softer, less saturated backgrounds, gentler accents and off-white text. Your selected theme stays selected; the new colors apply after updating. Settings → Color theme lets you try the other palettes. No new setup or pairing is required.

## New in 0.8.4

**Media stays in the conversation.** Open a person to see SMS and incoming MMS together, including chats with only media. Photos display inline; audio and video have local playback controls. Scroll to load older messages. Media → Take me there still opens the selected attachment in its surrounding history. Group or unconfirmed recipients stay read-only; outgoing MMS and RCS are not added.

**Understand & reply.** For an AI-enabled person, tap this control beneath incoming media. The connected AI can describe images/screenshots or a few sampled video frames and suggest a possible meaning and reply. It cannot hear or transcribe audio or watch the whole video. Missing or unsupported attachments show a limitation; uncertain facts, plans and private whereabouts still require your input. Read the interpretation, choose **Use reply** if available, then edit and send or schedule it yourself. Media analysis never automatically sends or starts a timer.

**Suggestions while chatting.** **Settings → Suggest replies while chatting** starts on. While a chat is open, it can prepare a reviewable suggestion for the latest incoming text or media from a person you have already enabled for AI. It does not change anyone's reply mode, enable cloud access, overwrite a typed draft or turn on automatic sending. Turn it off and save preferences if you want to tap Draft reply or Understand & reply yourself. The setting also applies to people using When I ask; their background mode remains unchanged.

**Learn from replies you actually send.** With **Match my recent texts** enabled, OpenAI can use up to 12 recent distinct final replies you manually approved and successfully sent to that person. Edited final wording counts; unreviewed automatic sends, unsent/failed replies and the fixed Delay acknowledgment do not. **Reply setup → Forget these examples** removes the currently selected examples from future requests without deleting messages. Later successful manual replies can become new examples. This guides each request; it does not fine-tune a model or erase provider records from earlier requests.

Media analysis shares selected images/frames, captions and conversation context with your configured service and OpenAI. Local playback does not. Actual Pixel playback, supported codecs and carrier MMS behavior still need phone testing; computer tests do not establish those results.

## New in 0.8.3

Open a conversation → **Reply setup → Humor**. Choose **Light · PG-13**, **Playful**, **Sarcastic**, **Edgy** or **Extreme**. Extreme allows welcome adult innuendo and stronger language, while remaining non-graphic. The level is a ceiling for this person, not a requirement to make every reply funny. Serious messages and your stated boundaries still take priority.

Add optional **Inside jokes**: explain the catchphrase, what it means and when you normally use it. For example: “We call each other five-star chef whenever one of us burns toast.” Tap **Save profile**. These notes stay separate for each contact and guide future AI drafts; they do not permanently train the model. They are sent with that contact's context when you use the connected AI service.

New and upgraded profiles start at Light with empty inside-joke notes. Selecting humor does not turn on cloud access or Autopilot. Your existing pairing, tone, conversation flow and reply mode stay saved. No new permission or API key is required. Generic local quick suggestions do not use these notes.

## New in 0.8.2

When Reply Pilot needs your attention, expand its notification:

- **Joke:** tells the AI that the current incoming message sequence is joking or teasing and asks it to reconsider. A revised reply is saved for your review; this button never sends an AI reply automatically. Genuine plans, unknown personal facts and requests to stop can still remain held. It is a one-request clarification, not a permanent personality setting.
- **Delay:** available for plans. Tapping it approves this exact SMS: **“I'll let you know in a bit.”** It sends once using your selected SIM. Planning stays paused until you send a substantive reply yourself. Delay is a short response, not a scheduled-send timer, and it does not require the AI service or internet.

The same buttons appear in the chat. If you have typed an unsent draft, send or clear it first. New messages, changed profiles and already-used actions invalidate older buttons. Android may ask you to unlock before applying a lock-screen action; your notification preview preferences remain in effect. If Android hides the actions, expand the notification or open the conversation.

Install the update in place. No new permission, API key or pairing is needed. The matching hosted Joke update is deployed. Real lock-screen rendering, carrier delivery and background-job timing still need testing on your phone; an already carrier-submitted SMS cannot be recalled.

## New in 0.8.1

Open your girlfriend’s conversation → **Reply setup → Girlfriend Autopilot**, then **Save profile**. The preset is available once there is enough conversation history. It turns on automatic sending for that contact, using your saved tone, relationship notes and examples. Use Train Pilot to practice your replies and Reply setup → Inside jokes for shared references.

A goodnight, going-to-bed message or clear request to stop pauses replies without sending another message. Repeated acknowledgments and emoji reactions do not wake it up. A new substantive message can resume it, or use **Resume** in the paused banner; Resume does not send the old bedtime reply. You can always write a message yourself. Planning and serious relationship questions still wait for your input. The existing five-consecutive-automatic-reply limit remains.

The matching hosted service update is deployed; the release validation record identifies it. No new permissions or pairing are needed. Already carrier-submitted SMS cannot be recalled, and unfamiliar wording may still require AI judgment.

## New in 0.8.0

- **Media → Take me there:** jump to the original photo/attachment and surrounding SMS/MMS. Browse older or newer messages; Latest conversation returns to the live composer. Group history is read-only.
- **Reply setup → Conversation flow:** Natural, Always reply or Keep conversation going. Always reply acknowledges okay/alright once; further acknowledgments stop until substantive incoming text. Planning still waits for your input.
- **Myself (beta):** emphasizes your own sent and practiced replies. The original About me page was replaced by Train Pilot in 0.9.4.
- **Settings → Location replies:** optional and off initially. Turn it on, allow Location while using the app, then use Android app settings → Permissions → Location → Allow all the time for background checks. Precise location is needed for nearby landmarks and home recognition. The rest of the app works without this permission.
- Refresh location and optionally save your home address or use your current location as home. Address lookup uses Android's geocoding service; finding nearby places uses Android geocoding and OpenStreetMap. Only the latest fix is saved. A coarse label, never raw coordinates or your saved address, is sent to OpenAI for an allowed person's current-location question.
- Open that person's **Reply setup → Allow location replies (including at home)** and save. Everyone else stays excluded. Checks are roughly every 20 minutes, subject to Android background/battery limits. Missing or stale fixes wait for your input instead of guessing. An unedited location-based AI timer cannot outlive the fix's 25-minute validity; changed location/settings also stop that old reply.

## Pinned conversations (added in 0.7.24)

**Pin chats.** On Messages, hold a conversation, then choose **Pin conversation**. A pin icon marks it, and it stays above unpinned chats. Use the same menu and choose **Unpin conversation** to return it to the regular list.

Pinned chats stay saved on this phone when the app closes or updates. Within the pinned group, the most recently active chat appears first. Search still filters pinned and unpinned chats. A pinned chat stays available even after it ages out of the recent unpinned inbox. Pinning does not turn on AI, send messages, alter drafts or change timers. No server update or new pairing is required.

## New in 0.7.23

**Plans need you.** Invitations, availability questions, confirmations and changes of plans are handed to you. The app alerts you and shows **Plans need you** in the chat. Automatic replies for that person stay paused across incoming texts, profile edits and app restarts until your manually written reply is confirmed sent (or delivered). Typing, editing or trying an AI draft does not clear the hold. Failed or unconfirmed sends keep it paused. Manually chosen sends and timers remain available.

**Wait for the whole message.** Automatic drafting waits for a 10-second quiet period after the latest incoming SMS. Each new text restarts it. The reply uses all consecutive unanswered incoming messages, including corrections; switching off style matching still preserves that sequence. New incoming text discards an unfinished AI result and stops an old automatic timer. Autopilot sends a reply once that wait and generation finish; it does not skip the quiet period. Large bursts that exceed the supported context are held for review.

The hosted service has matching planning checks. Install this phone update for the persistent hold and burst timing. Your existing pairing and settings are retained. Detection depends partly on AI judgment and is not guaranteed; carrier/background behavior still needs testing on your Pixel. A text already submitted to the carrier cannot be recalled.

## Fixed in 0.7.22

Fixes the Android “Syntax error in regexp pattern” that could leave chats on Loading conversation. The same fix protects generated reply-length checks. Failed chat loads now retain Back and offer Retry. Install this update over your existing app; keep it installed so pairing, profiles and drafts remain. Random ranges and existing reply settings stay saved.

## New in 0.7.21

**Random range.** Set a minimum and maximum delay, such as **5–30 minutes**. Each new reply gets one randomly chosen delay within that range, including seconds; opening the app or refreshing does not change its scheduled time.

- For a person: **Reply setup → Send after timer → Automatic send delay → Random range**. Enter From/To minutes and save the profile. This is automatic sending without reviewing each reply; existing history and safety checks still apply.
- For Sleep: **Settings → Sleep schedule → Reply delay → Random range**. Set the range and cutoff, then start the schedule. A selected send time at or after cutoff stays unsent for your review; it is not shortened or randomly chosen again.
- For one message: hold the send arrow → **Random range**. Enter the bounds and tap **Set timer**. The Android confirmation shows the range and exact chosen delay; that countdown starts after you confirm.
- For the default manual timer: **Settings → Default delay → Random range**, enter the bounds and save preferences.

Bounds use whole minutes from 1 to 10,080 (seven days). From cannot exceed To; equal values give a fixed delay. Existing fixed timers and Autopilot settings stay saved. Autopilot remains immediate unless an active Sleep schedule overrides it. Actual carrier delivery can occur later than the scheduled submission.

## New in 0.7.20

**Autopilot** is the new name for the per-person immediate-reply option in **Reply setup**. Its behavior is unchanged: fresh automatic AI replies send as soon as they are ready, without individual review or an added delay. Your saved modes and pairing stay in place. An active Sleep schedule temporarily uses its selected delay instead and pauses automatic replies at the cutoff.

## New in 0.7.19

**Simpler timers.** The send menu, each person's automatic timer, and your default delay now offer **1 minute, 5 minutes, 30 minutes, 1 hour, or Custom**. Existing 10- or 15-minute settings remain saved under Custom. Tap the send arrow to send immediately; hold it to schedule.

**Sleep schedule.** In **Settings → Sleep schedule**, choose the delay for new automatic replies and a stop time, then start the schedule. For example, a five-minute delay with a midnight stop means each fresh AI reply waits five minutes, and automatic replying pauses at midnight. Enable precise timers using the link in this section if needed. It only applies to people already enabled for automatic sending. It responds to incoming messages; it does not repeatedly send unsolicited messages every five minutes.

The stop time is the next occurrence of the selected time on your phone, including the next day when needed. A reply whose timer would finish at or after the stop time stays unsent for manual review. After the cutoff, automatic drafting and sending remain paused until you choose **Resume normal replies** or start another sleep schedule. Your existing contact modes and delays stay saved. You can also pause early. The existing history, repeat and five-consecutive-reply safeguards still apply.

Starting, changing, pausing or resuming Sleep stops pending automatic timers. These controls do not send existing drafts, enable contacts or change manually accepted timers. Manual writing, sending and requested AI drafts remain available. Clock changes and phone restarts pause an active schedule for review.

## New in 0.7.18

**Personal replies, not tutorials.** Requests such as “explain how to code in Java,” writing an essay, doing homework or overriding the reply agent’s rules are held for your input. The agent also has instructions to hold replies that need unknown personal facts, private credentials or unsupported claims. AI output is limited to 360 characters and 60 words; an overlong answer, code block or long list is withheld instead of being cut off. You can still type and send your own message.

**Enough history first.** Automatic modes are available only with at least 20 distinct usable messages for that person, including five from you and five from them. Recent SMS and an added labeled chat log can contribute. Known automatic replies are excluded from the owner examples used for this readiness check. Existing automatic settings are blocked when the requirement is unmet. Saving a log does not enable automatic replies: select a mode yourself after there is enough context.

**Add a person's chat log.** Open their conversation → **Reply setup → Chat log for this person**. Import a plain-text `.txt` file (up to 256 KB), or paste a log using **Me:** and **Them:** labels. For a two-person WhatsApp text export, enter your own name exactly as it appears in the file. Tap **Check chat log**, review the normalized speaker labels and counts, then **Save profile**. Group exports and ambiguous speakers are rejected.

Only the latest complete messages that fit the 50-message / 8,000-character limit are kept, with a notice if anything was omitted. **Remove log**, then **Save profile**, removes its examples. The imported text stays separate from your displayed SMS history. Checking/saving a log is local; enabled AI requests send selected examples to your hosted service and OpenAI. This supplies context for replies, not permanent model training.

Update the app in place for the new settings and import controls. Older apps cannot provide the new readiness confirmation, so the updated service withholds their automatic replies until you update. Pairing and API keys remain unchanged.

## Loop protection from 0.7.17

Automatic replies can now stop naturally. A closing “Ok,” “thanks,” or thumbs-up after your reply no longer has to produce another message. The AI can choose **No reply needed** when the conversation is complete or the next reply would repeat what you already said. That decision is saved for the incoming message, so reopening the chat does not try again.

A separate phone safeguard pauses automation after **five consecutive automatic send attempts** for the same person. It stays paused across restarts; send a message yourself in Reply Pilot to resume after successful carrier acceptance. New incoming messages still arrive normally, and you can always type and send a manual response. The **Draft reply** button now prepares a reply for you to review; only background generation uses automatic sending.

Update the app in place for the saved decisions, quiet status display and phone safeguard. Your existing hosted draft service is updated and verified with fictional conversations. Other self-hosted copies must deploy the updated relay. Older phone versions will not send an empty no-reply result, but may display “Draft needs attention” until updated. No new key or pairing is needed.

## SMS delivery reports from 0.7.16

Delivery reports are requested automatically for new SMS messages. Under your outgoing text, **Sent** means your carrier accepted it; **Delivered** with two check marks means delivery was confirmed. **Delivery failed** appears for a carrier-reported failure. Inbox and Queue also update when the report arrives. Long texts require confirmation for every part before the whole text shows Delivered.

A missing report does not mean the message failed: some carriers do not provide delivery reports. **Delivered is not Read**; SMS does not provide read receipts. Reports cannot be requested retroactively for old messages, though existing stored reports can be shown. Confirmation time reflects when Reply Pilot received the report. No extra delivery sound is played and no message is automatically retried.

**RCS remains unavailable in this APK.** Android’s carrier RCS interfaces require a privileged approved app; this normal installation cannot use them. The newer alternative-transport service calls the default SMS app’s own transport and does not provide Reply Pilot access to Google Messages/Jibe. See [Android IMS access requirements](https://source.android.com/docs/core/connect/ims-single-registration) and [the alternative-transport API](https://developer.android.com/reference/android/service/messaging/AlternativeMessageTransportService). A Google Messages companion workflow would be a separate change to how the app drafts and sends.

## Contacts from 0.7.15

Tap **+** on Messages to open **New message**. Search by a saved contact’s name or phone number, then tap the correct Mobile, Work or other labeled number to open the conversation. Write your message and tap the send arrow when ready. Choosing a contact does not send a message.

If contacts access is off, tap **Allow contacts** and accept Android’s Contacts permission. This can also be managed under **Settings → Phone setup → Contacts**. If Android has blocked further prompts, **Open app settings** takes you to Reply Pilot’s App info; choose **Permissions → Contacts → Allow** and return. You can always enter a phone number and tap **Start conversation** without contact access.

Contacts are searched on your phone and are not uploaded for lookup. Type more of a name or number if the list asks you to narrow the results. No new contact permission is added to the installer; this update provides a dedicated access control and picker for the existing Contacts permission.

## Live inbox updates from 0.7.14

The Messages list updates when an automatic or manual reply starts sending and when the carrier reports its result. The latest preview, time and conversation order refresh without reopening the app. Search text, cursor and your reading position stay in place. Sending and failed messages have clear status labels.

## Search and Media improvements from 0.7.13

**Find a conversation** stops highlighting when you tap outside it, dismiss the keyboard, press Enter/Escape or leave the inbox. Your search text stays saved.

**Media** opens immediately while attachments load, with a small animated paper plane following a dotted flight path. Recent results stay available briefly for quick returns; Refresh checks again. Photos load as you scroll, with a placeholder and a readable error if a photo cannot open. Reduced-motion settings use a still icon.

## Tap and hold sending from 0.7.12

The reply field now has a standard send arrow. **Tap once to send immediately.** Hold it for about half a second to open the timer menu: 1 minute, 5 minutes, 30 minutes, 1 hour, or Custom. Releasing the hold does not send. Choosing a timer still opens the existing confirmation.

You can also tap **Hold to schedule** below the field, or focus the arrow and press Shift+F10/Down Arrow. Enter or Space on the send arrow sends immediately. Your existing automatic-reply modes stay saved.

## Chat surfaces from 0.7.11

The contact header and bottom reply area now have their own stronger theme-colored surfaces and thin dividers. The conversation uses a deeper background, and the rounded text field stays visibly separate from its surroundings. All ten themes are supported.

## Launcher icon from 0.7.10

The launcher icon now has a midnight background, blue-to-violet message bubble, paper plane and cyan spark. It adapts to your launcher’s icon shape, with a separate monochrome version for Android themed icons. Notifications use a matching transparent white message/plane symbol. Tiny Blast and your notification settings remain unchanged.

Install the update in place. If Pixel’s themed icons are enabled, the icon uses your wallpaper colors. The 50-message context and full SMS history from 0.7.9 are included; your hosted service is already updated and no new pairing is needed.

## History and recent context from 0.7.9

**Full SMS history:** Open a conversation and scroll upward. Older messages load automatically without a total history cutoff. A load/retry control is available if a page cannot be retrieved. The app keeps your reading position and edited reply while history updates. This shows messages stored in Android’s message database; it cannot recover deleted texts or Google Messages-only RCS history. Version 0.7.9 introduced 50-message SMS pages; 0.8.4 combines SMS and MMS in the live timeline as well as Media.

**Learn from recent conversation:** After SMS access is granted, opening the app prepares up to 50 incoming and sent texts for each person whose AI is enabled. Opening a chat and generating a reply refreshes that context. Reply setup shows how many messages are ready and how many you sent. Only actually sent messages teach your writing style; queued, failed and unsent drafts are excluded.

Keep **Settings → Match my recent texts** on to use the recent 50 SMS and, from 0.8.4, approved reply examples in OpenAI drafts. This uses examples as request context rather than training a separate model. Message preparation is local; a draft request shares selected context with your configured service and OpenAI. Older context texts can be shortened to 600 characters, while the unanswered incoming sequence is preserved within the existing per-message 1,600-character limit. Displayed history is full text. Switching matching off omits selected style examples and keeps eight immediate turns plus explicit profile samples, retaining the complete supported unanswered sequence even when it is longer.

The companion service must also support the expanded history. Your existing Reply Pilot Railway service has been updated and tested with a fictional 50-message conversation; the same key, endpoint and phone pairing are retained. Other self-hosted installations must deploy the updated relay before using 50-message drafts. Optional Gemini Nano/Quick Reply retain their smaller on-device context limits.

## Deeper themes from 0.7.8

All ten themes now use richer, deeper colors throughout the app, including backgrounds, cards, message bubbles, buttons and navigation. The theme picker previews each palette. Midnight stays the default, and an already saved theme choice stays selected. Choose a theme in **Settings → Color theme**.

## Notification sound from 0.7.7

**Tiny Blast** is the new default sound for incoming messages and prepared replies/timers. The supplied, roughly two-second MP3 is included unchanged in the app. This does not change your phone’s global ringtone or notification sound and needs no new permission.

New installations and untouched default notification channels use Tiny Blast. If you customized a channel’s sound, muted or blocked it, or changed its privacy settings, the update keeps that channel and your choices. See **Sound and lock screen** below to change the sound yourself.

## Keyboard layout from 0.7.6

The chat now adjusts to the visible space when the keyboard opens. It keeps the reply field and send control above the keyboard, limits how tall a long reply can grow, and updates the layout when you focus a field or return to the app without replacing your editor or cursor.

Before testing, confirm **Version 0.8.9** in Settings. Open a conversation and tap the reply field. Check that the reply and send arrow remain above the keyboard, then type a long reply, hide and reopen the keyboard, and briefly background the app and return. Long replies should scroll inside the field, and your edited text should remain saved.

## Midnight theme from 0.7.5

**Midnight** remains the default navy theme. Version 0.7.8 deepens its colors along with the other nine themes in Settings.

The first open of 0.7.5 or later selects Midnight once, in both the phone app and browser preview. If that migration already ran, 0.7.23 keeps your subsequent theme choice. Your pairing, conversations, drafts, contact profiles, sending modes and other settings are preserved. Choose another theme whenever you like; subsequent launches keep that choice.

## Inbox layout from 0.7.4

The inbox has a compact Messages header with the new-message button on the right, followed by the search field. Android status icons, camera cutouts, navigation controls and the keyboard get their own space outside the message interface.

After updating, check that the clock/status icons sit above the header, then open a chat and show/hide the keyboard. The conversation header and reply box should remain visible. These layout improvements remain in 0.7.23. Pairing and reply settings stay saved; the one-time Midnight selection described above applies separately.

## Earlier theme change in 0.7.3

Version 0.7.3 introduced the charcoal Messages theme. Version 0.7.5 restores the original navy Midnight name and palette while retaining dark native launch, window and dialog styling.

## Typing improvements from 0.7.2

Typing now batches draft saves after a brief pause, while the first edit still cancels a running automatic timer immediately. Navigation, sending and app backgrounding flush the latest text. Background updates preserve the active editor and cursor. SMS checks and read updates do less work. Message bubbles, search, buttons and settings use a softer rounded layout with system typography and larger message text. The “you have the final say” banner is absent.

After updating, type a longer reply, switch between chats, briefly background the app and return to confirm the keyboard feels smoother and the text remains saved. These typing improvements preserve pairing, profiles and Autopilot/delayed modes. The one-time color change described above applies separately.

## The new layout

- The compact **Messages** header and new-message button sit above **Find a conversation**.
- **Settings → Color theme** has Forest, Ocean, Lavender, Rose, Sunset, Slate, Midnight, Mocha, Mint and Plum. Tap to save immediately.
- In a chat, **Reply setup** is beside the SMS badge at the top right. Tap it for the per-person form. Save profile applies your choices; closing the dropdown keeps unsaved edits until you leave the app.
- The composer at the bottom contains the suggested reply. Edit it as usual. Tap the **send arrow** to send immediately, or hold it to schedule. Choose **1 min, 5 min, 30 min, 1 hour**, or **Custom** (1 minute to 7 days). Confirm the exact recipient, reply and delay in the Android dialog to start a manual timer.

## Autopilot for a specific person

Open their conversation → **Reply setup → Autopilot → Save profile**. Keep **Settings → Prepare replies automatically** on. Only that person's saved profile changes.

The app generates an OpenAI reply and sends it as soon as the reply is ready, without review, a confirmation dialog or a countdown. There is no cancellation window after submission. The AI request and carrier still take time; Autopilot adds no delay after generation.

Saving the setting does not send the draft already on screen. Future replies generated automatically for new incoming SMS use the mode when Android schedules them. Explicit **Draft reply** or **Redraft** requests remain for your review. The original message and reply appear in a notification, which updates with the carrier outcome without a second alert. Try AI never sends anything.

An active **Sleep schedule** overrides Autopilot with the selected delay and requires precise timers. At the Sleep cutoff, automatic drafting and sending pause until you resume. The person's saved Autopilot choice stays in place.

To restore a delay, choose **Send after timer**, pick the delay and save. Changing from Autopilot to Send after timer starts with five minutes. Choose **Prepare automatically** to review each reply yourself, or **Off** to stop AI replies to that person.

## Optional automatic sending

Existing profile choices and delays are preserved. **Autopilot is never enabled by an update.** To enable delayed automatic sending for a person:

1. Open the chat → **Reply setup → Send after timer**.
2. Choose the automatic send delay. Choose a tone and relationship, and add optional dynamics or samples.
3. Tap **Save profile**. Keep **Settings → Prepare replies automatically** enabled.
4. The next reply generated automatically for a new incoming SMS starts its timer without an approval dialog. Saving the profile does not schedule the old draft. Explicit **Draft reply** or **Redraft** requests remain for your review.

Each automatic timer posts a notification with the original text, proposed reply, countdown and **Cancel timer** action. Tap to open the chat. Editing the reply cancels its automatic timer; afterwards, tap the send arrow to send your edited text or hold it to schedule. You can also cancel from the chat or Queue. Once carrier sending has begun, editing and cancellation cannot recall the SMS.

**Prepare automatically** remains available if you want background drafts prepared but want to confirm every send yourself. **When I ask** disables background drafting; the separate **Suggest replies while chatting** setting can still prepare reviewable suggestions while that person's chat is open. Switch that setting off to use only your Draft reply/Redraft or Understand & reply taps. **Off** disables cloud drafting for that person. Selecting Off or saving changed profile settings stops that person's pending automatic timers. Turning global automatic replies off, changing the SIM or disconnecting pauses automatic timers; manually confirmed timers must be cancelled separately.

The phone must be the default SMS app, have SMS/notification access and an active SIM. Delayed sends also require precise-timer access; Autopilot's immediate sends do not, unless a Sleep schedule applies a delay. If an alert cannot post, the draft remains available and no automatic timer is armed. A new incoming message pauses the previous reply. A restart, app update, clock change or lost access also pauses timers. Late or uncertain sends never retry automatically.

## Sound and lock screen

**Settings → Show message previews on lock screen** controls whether the original message and suggested reply are public in draft/timer notifications. It starts on as requested. Turn it off to hide content from the lock screen. Anyone who can see an unlocked preview can read it.

**Notification sound & lock screen** opens Android’s settings for the reply-notification channel the app is actually using. Tiny Blast is the default for new or untouched channels; existing custom sounds, silent/blocked settings and privacy choices remain in place. Android’s volume and Do Not Disturb settings still apply.

You can choose a different sound there. For incoming texts, open Android Settings → Apps → Reply Pilot → Notifications and select the incoming-message category. If you want Tiny Blast on a previously customized channel and it is not listed, use Android’s **My Sounds** / add-sound option to select your original **Tiny Blast.mp3** file. Bundling the sound in the app does not necessarily add it to Android’s sound picker.

Expand a notification to see more text. Android limits notification size and may hide or truncate content; apps cannot demand unlimited lock-screen space or override your privacy settings. Opening Reply Pilot shows the full conversation and editable reply.

## Pairing and phone setup

An already paired installation keeps its connection. For a fresh installation, configure the always-on HTTPS service and save the OpenAI key privately there. The OpenAI key never belongs in the phone app or this chat. Open **Settings → Phone pairing code → Save connection**, then **Check connection**. A real Try AI reply tests provider access and billing.

**Try AI → OpenAI** accepts a test message, relationship dynamics and optional samples. This test has no recipient or send action. It cannot start a timer, including when a real person's profile uses automatic sending. Gemini Nano remains an optional local test choice. The computer browser is a synthetic interface preview, with real model testing disabled.

To use SMS, complete Settings → Phone setup, select your SIM, and grant notifications and precise timers. If Android's default chooser does not finish, use **Open Android default apps → SMS app → Reply Pilot**. If sensitive access is blocked, open **Android Settings → Apps → Reply Pilot → ⋮ → Allow restricted settings**, authenticate and retry. Do not uninstall or clear app data to resolve this.

## Device checks and limits

Test manual and automatic sending with a second phone before relying on it. Confirm the original/reply notification, sound, countdown, cancellation, edits, new incoming texts, device restart and permission changes. The computer tests simulate phone interactions; they do not prove actual carrier delivery, lock-screen rendering or background scheduling on your Pixel.

Individual SMS and carrier MMS attachment sending/receiving are implemented. RCS and group replies are not supported. Turn off RCS in Google Messages before switching; switch back via Android Settings → Apps → Default apps → SMS app when needed. MMS depends on your carrier's settings and limits and may require mobile data; verify a small photo in both directions on your phone. OpenAI needs internet to create new drafts; a saved timer does not make another AI request.

Enabled conversations share selected messages, profile context, samples and approved reply examples through the hosted service to OpenAI. Media analysis also shares selected resized JPEG images/video frames and captions; it sends no audio for transcription. These are request context, not personal model training. Profiles and drafts remain in app-private phone storage with backups disabled. The API key stays on the server, response storage is disabled and provider retention still applies. See `relay/README.md`, `VALIDATION.md` and `SECURITY-AUDIT.md` for details.
