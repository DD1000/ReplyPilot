# Reply Pilot 0.12.2 — Android personal preview

An Android SMS/MMS app with per-chat **Train Autopilot**. Tap the send arrow to send manually or hold it to choose Instant, 1 minute or 5 minutes. For each chat you choose, Autopilot learns how you text that person and replies in your voice; it defers decisions and plans and alerts you when a chat needs attention. The APK contains no OpenAI key; your paired HTTPS service holds it.

Training is per chat and only starts when you tap **Train Autopilot** in that chat's Reply setup. The phone reads your most recent 1,000 SMS/MMS texts with that person, the service asks the training model (GPT-6 Astra by default) once, and the phone saves the resulting persona encrypted in private storage: how you text them, the relationship, ongoing context, things to avoid, and up to 30 of your real replies as examples. Each Autopilot reply sends that persona and the recent conversation to the reply model (GPT-6 Sol by default). Nothing about a chat goes to the service until you train it. This is contextual memory, not model training. RCS chats are not available to this app.

**Status:** personal preview, version 0.12.2 (version code 68). The existing draft service is deployed to Railway, and the user previously reported successful phone pairing and test replies. See **VALIDATION.md** for completed checks by version. Carrier, background-send and physical Pixel media/codec tests remain outstanding. The browser preview uses synthetic data. ARM64 Android 12+; compile/target SDK 36. Pixel 11 compatibility requires testing on the actual phone.

## New in 0.12.2

- **Autopilot answers opinion questions instead of dodging them.** Questions like “what do you think AI will do to the economy?” used to be treated as chatbot work: Autopilot held back and the phone swapped in “Got your message. Let me get back to you.” Now opinion and big-picture questions get a short, casual take in your texting style, with no alert. Real work requests (write code, an essay, homework) still get a casual brush-off in your voice and an alert.
- **Questions about you get answered too.** “How would you use it?” used to get “Let me get back to you on that.” Now Autopilot answers from your views, your trained persona and what you've said before. When none of those cover it, it gives a short general answer without made-up details and alerts you so you can add more. Only truly private things (where you are, your address, schedule, money, health, codes or accounts) still get a deferral.
- **Settings → Your views.** Type what you think about things people might ask you (up to 1,200 characters). When someone asks your opinion, Autopilot uses these views and never contradicts them. Without them it keeps opinions light and stays neutral on divisive political or religious questions.
- **Fewer canned lines.** When Autopilot flags a chat for you, it keeps its own reply in your voice when that reply is safe. The fixed texts are used only if the AI is unreachable or its reply isn't safe (a commitment, a claimed location, or anything that looks like a number, link, email, address or code).
- **Auto Updater.command.** Start it once and leave its window open: whenever Claude saves an update, the Mac updates the server (only if the server changed), builds the app (only if the app changed) and pushes to GitHub by itself, then shows a notification when the new APK is ready. It never deletes anything and stops at the first problem.
- Requires the 0.12.2 server update. Install over 0.12.1 without clearing storage. No new permission.

## Previous release: 0.12.1

- **Autopilot no longer repeats itself about plans.** Before, every plan question got the same fixed text (“I'll let you know in a bit.”), again and again. Now the first plan push gets a short brush-off written by the AI in your voice for that chat, and a second push gets one different follow-up. From the third push on, Autopilot stays quiet and alerts you each time (“Chat needs attention”) until you reply yourself. It still never agrees to, declines or suggests a time, and a deferral never repeats a text already in the chat. Plan deferrals more than 12 hours old, or from before your own last reply, don't count.
- **Replies while the phone is locked.** Android can hold background work until you unlock the phone, which delays Autopilot. Settings → Phone setup has **Reply while locked → Allow**, and an Autopilot chat shows the same prompt until you allow it. Android asks once; afterwards Autopilot's reply jobs also run at high priority.
- Required the 0.12.1 server update for AI-written deferrals. Added one Android permission so the app can ask for the battery exemption; nothing changes until you tap Allow.

## Previous release: 0.12.0

- **Train Autopilot, one chat at a time.** Reply setup has a Train Autopilot panel. Tap it to build that person's persona from your most recent 1,000 texts together (usually under a minute). You can see what it learned, retrain anytime, or remove the training. After about 1,000 new texts the app suggests retraining; chats with under 100 texts show that the persona will be thin.
- **Autopilot requires training.** Autopilot can only be turned on for a trained chat, and removing training stops it. Training never turns Autopilot on by itself. Explicit Draft reply and Understand & reply still work for untrained chats and are reviewed before sending.
- **The whole-phone history pass is gone.** 0.11.0 sent every conversation to OpenAI before any AI reply worked and paused all AI until it finished. 0.12.0 deletes those old all-conversation summaries from the phone on first launch.
- **Models:** training uses `OPENAI_TRAINING_MODEL` (default `gpt-6-astra`) and falls back to the reply model if your key can't use it; replies use `OPENAI_MODEL` (default `gpt-6-sol`).
- Required the 0.12.0 server update. Installed over 0.11.x without clearing storage. No new phone permission.

## Previous release: 0.11.1

- **Sending SIM fix.** When Android reports the phone's only SIM under a new ID (for example after a SIM, eSIM or carrier change), sending now uses that SIM instead of stopping with “Select an active SIM in Settings.” Phones with several SIMs still ask you to choose.
- An empty **Settings → Sending SIM** menu now explains why: missing Phone access (with **Allow Phone access** and **Open app permissions** buttons) or no active SIM reported by Android. Send errors name the same fix.
- Choosing a sending SIM saves immediately. A SIM without a display name still appears, labeled by carrier or slot. If Android's SIM list is briefly empty, the confirmed default texting SIM is used.
- Install over 0.11.0 without clearing storage. No new permission, pairing or service update is needed.

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

The following versioned notes are historical; current controls are described above.

- **New in 0.9.13 — accurate automatic-reply history:** eligibility checks older, current-provider SMS and MMS text instead of only the newest 50 SMS. Recheck history refreshes readiness while preserving notes and modes. The requirement remains 20 distinct usable messages including 5 from each person; known automatic outputs do not count as owner examples.
- **New in 0.9.12 — Android share menu:** receive shared text, links, photos, supported video/audio and contact files. Choose a recipient, review the share, then explicitly add it to an owner-edited draft. Existing draft text is preserved; files use the existing bounded private MMS staging and require a final Send tap. Repeated resumes do not duplicate imports.
- **New in 0.9.11 — history loading and recovery:** opened chats and older live pages use independent bounded readers, so the full inbox does not block viewing them. Late saved pages cannot replace a completed live read. Saved-page retries retain their position and recover once from a changed archive snapshot. A first rebuild shows progress instead of a false access error; unchanged scans keep pagination stable, and already-read chats avoid empty provider updates that could restart syncing.

- **New in 0.9.10 — default-app setup recovery:** Android role status updates independently of history loading. A missing chooser callback can recover on return or after a bounded wait; Check status and Open Android default apps remain available. Retried requests ignore stale results.

- **New in 0.9.9 — complete available history and MMS practice:** cached/live history gaps now fill automatically, reopening keeps already-loaded rows and failed page retries keep their correct cursor. Phone-backed MMS text is read alongside inline text. Settings shows saved conversation/message counts, sync state and Refresh saved history. Train Pilot learns from readable SMS and MMS text, including older incoming behind a burst of outgoing messages; it has no age cutoff. Practice recovery shows one primary action with more spacing. Only history exposed to this app by Android can be read.

- **New in 0.9.8 — contact photos beside conversations:** the launch inbox now supplies current local thumbnail routes, so saved chats can show contact photos before the full inbox refresh. The first twelve rows load photos eagerly, including pinned chats; later rows stay lazy. Missing photos keep initials.

- **New in 0.9.7 — quicker Train Pilot:** the button sits in the reply composer above the text field. Practice opens before live chat history finishes loading, with dedicated bounded workers for saved state and scenario generation. Reopening a pending scenario checks its progress automatically. Starting a scenario still requires an explicit tap and an online AI response.

- **New in 0.9.6 — attachments only in Media:** text-only MMS no longer produce media cards or crowd older attachments out of the gallery. Photos, videos, audio and files keep their captions and Take me there link. Ordinary texts remain in their conversations.

- **New in 0.9.5 — Reply setup without waiting:** the control opens immediately, independently of conversation history. Settings can be edited while current recipient details are checked; saving waits for that check, preserving unsaved edits. Group conversations explain their read-only limitation.
- **Contact photos:** saved Android contact pictures appear in the inbox, chat header and contact picker when Contacts permission is allowed. Missing pictures use initials. Photos stay local and are not sent to the AI.
- **Full saved history:** a background sync saves all available SMS/MMS conversations and their text/history metadata in an encrypted private archive. Small cached pages open while the phone refreshes; older pages load as you scroll. There is no fixed limit on the number of chats or messages saved. Initial sync and storage capacity still matter; attachment files stay in Android's message storage and load when needed. This expands the earlier 20-summary/10-chat cache described in historical release notes below.

- **New in 0.9.4 — Train Pilot:** open a one-to-one chat and choose Train Pilot above the text field. Start a private practice scenario based on that contact’s recent messages, then reply as yourself. Practice examples guide future drafts for that person, without sending practice text to them or changing automatic-reply settings.
- **Explain meaning:** tap a received message and explain what it meant. The note stays bound to that message and person; edit or remove it the same way. Notes help interpret future context without overriding planning handoffs or reply safeguards.
- The About me page is removed. Myself (beta) now emphasizes your own sent, approved and practice replies. This supplies context on future requests; it does not fine-tune a permanent personal model.

- **New in 0.9.3:** the supplied coral R/reply-arrow on purple is the launcher icon, with adaptive-mask spacing and a matching monochrome mark for themed icons.

- **New in 0.9.2 — reliable theme taps:** theme controls stay intact during refresh/touch, selected indicators update immediately, and rapid taps keep only the newest pending save. Nested swatches and labels select the same theme; delayed older saves cannot replace a newer choice.
- **Accurate inbox text:** MMS text/captions now appear in live and saved snippets. Attachment-only messages retain a media label, and drafts/protocol reports cannot displace the newest conversation message. Previous snapshots that erased MMS text rebuild after one successful refresh.
- **Saved chat history:** up to30 recent message texts/placeholders from each of the10 most recently active chats are encrypted on this phone, alongside the existing20 inbox summaries. They display before provider reads finish. Background refresh is coalesced, and cached pages never provide sending authority, AI context, drafts or attachment bytes. Open the updated app once and let its initial message load finish to seed this cache.

- **New in 0.9.1:** cleaner chat rows without three-dot buttons. Press and hold a conversation to pin or unpin it; desktop right-click and Shift+F10 remain available.

- **New in 0.9.0 — immediate preferences:** the saved theme is applied to the Android launch document before its first render. Theme changes and pins save on separate ordered workers instead of waiting for messages to load. Pin/unpin moves the row immediately, survives stale refreshes, and rolls back with Retry if storage fails. Saved launch summaries use the latest durable pins.
- **Confirmed home addresses:** Settings → Location replies → Home → Use current place obtains a fresh precise fix and shows up to ten actual nearby numbered street records. Review the address, use Next address/Previous, then explicitly save; nothing is saved by searching alone. Manual entry remains available and preserves apartment/unit details. GPS and map data are estimates, not an exact-house guarantee. Only allowed contacts can receive the existing general “at home” reply; the home address stays outside the AI location context.

- **New in 0.8.9 — link previews:** HTTP/HTTPS links in fresh SMS/MMS text are tappable. The first link in each message can show a title, website, description and thumbnail from public HTTPS metadata. Only visible cards in an open conversation load previews; chats and typing stay responsive. Tapping opens the original link in Android’s browser or a supported installed app. Settings → Link previews controls automatic preview loading.
- **New in 0.8.8 — saved recent chats:** the latest 20 conversations are saved as a small encrypted preview on this phone. After the first successful inbox load, later launches show the saved list while fresh messages arrive quietly. Opening a saved chat keeps typing available while current conversation details load. The snapshot contains short summaries only; full history remains in Android’s message store.
- **New in 0.8.7 — faster navigation:** conversations display available history immediately, then refresh quietly from the phone. Recent and pinned chats are preloaded locally; typing and navigation do not wait for draft saves. Warm-up reads do not mark messages read, request AI, or send anything. Older history remains available as you scroll.
- **New in 0.8.6 — send and receive attachments:** the paperclip opens Android Files. Stage up to six supported photos, GIFs, short videos, audio files or contact cards for one person, add an optional caption, then tap the arrow to send by carrier MMS. Photos are resized to fit carrier limits; oversized files fail visibly. Attachment sending is manual and immediate; text timers remain separate. Incoming downloads have per-message status and explicit retry. Unknown sending outcomes never retry automatically.
- **New in 0.8.5 — softer themes:** all ten themes use muted backgrounds, gentler accents and off-white text. Chat headers and reply controls remain distinct. The chosen theme is preserved.
- **New in 0.8.4 — media in the conversation:** SMS and incoming MMS share the live timeline, including conversations that contain only media. Photos, audio and video open locally; playback depends on the phone's supported codecs. **Understand & reply** asks the connected AI to describe images/screenshots or sampled video frames, offer a tentative interpretation and, when appropriate, a short reply for review. Video analysis does not hear or transcribe audio or inspect the complete motion. Media replies never send or start a timer automatically; **Use reply** puts a chosen suggestion into the editable composer.
- **Suggestions while chatting:** Settings → **Suggest replies while chatting** starts on and can prepare a reviewable suggestion for the latest incoming text or media while its chat is open. It requires that person's existing AI opt-in and your saved connection, preserves typed drafts, and does not enable cloud access or automatic sending for anyone. Turn it off to request drafts/media analysis yourself.
- **Approved reply examples:** With **Match my recent texts** on, up to 12 recent distinct, successfully sent manual final replies for that person guide new OpenAI drafts and media suggestions. Unreviewed automatic replies, unsent/failed replies and the fixed Delay acknowledgment are excluded. Reply setup → **Forget these examples** removes the currently selected examples from future requests; it leaves the chat intact, and later successful manual replies can become new examples. This is request context, not model training or fine-tuning.

- **New in 0.8.3:** Each contact has a Humor slider: Light · PG-13, Playful, Sarcastic, Edgy and Extreme. The highest level allows welcome adult innuendo and stronger language while staying non-graphic. Optional Inside jokes (2,000 characters) supplies private callbacks for that person. Open a conversation → Reply setup → Humor, then Save profile. This ceiling applies to every tone, including Myself (beta), without changing reply mode or cloud consent. Serious context and boundaries still take priority; the setting never requires a joke. Notes guide new OpenAI or optional Nano drafts, not permanent model training or generic local quick suggestions.

- **New in 0.8.2:** Attention notifications and held-message banners offer **Joke** to reinterpret the current incoming message sequence and prepare a new draft for review. Planning holds additionally offer **Delay**, which sends exactly “I'll let you know in a bit.” once when tapped and keeps the plan waiting for the owner. Notification actions require device authentication; lock-screen privacy settings still apply. Actions are bound to the exact current message and saved settings; old or duplicated actions cannot send a second deferral. Delay needs no AI connection, and neither it nor Joke automatically resolves a planning hold.
- **New in 0.8.1:** Girlfriend Autopilot is an explicit per-contact preset in Reply setup. It uses short warm replies, your real personality and humor notes, and occasional relevant follow-up questions. It never invents your experiences or promises. Goodnight, going to bed, and requests to stop pause automatic replies immediately on receipt; a newer substantive incoming message or your Resume choice can lift that pause. Resume waits for new input and never sends a reply to the old bedtime text. Planning holds, location permission, history eligibility, acknowledgment-loop protection, burst waiting and the five-consecutive-reply cap still apply.
- **New in 0.8.0:** Media → Take me there opens that exact MMS inside a paged mixed SMS/MMS timeline. Earlier/later navigation is independent of the live composer; Latest conversation explicitly returns to current SMS. Groups and unconfirmed recipients remain read-only.
- **Reply engagement:** Natural, Always reply, or Keep conversation going per person. The latter modes acknowledge an ordinary okay/alright once, then stop repeated acknowledgments until a substantive incoming message. Planning, history readiness, unsupported-task, duplicate and five-automatic-reply limits remain.
- **Myself (beta):** Your sent and practiced replies guide your voice. Contact-specific relationship and inside-joke notes remain available. Humor is contextual, not guaranteed; serious or ambiguous messages should not force a joke.
- **Optional location replies:** Off by default; each person needs a separate Allow location replies opt-in. A periodic Android job requests a fix roughly every 20 minutes when background location is allowed. Android may delay or skip it. Only the latest fix is kept; fixes older than 25 minutes, clock/boot changes, missing permission or uncertain home/landmark accuracy cannot produce an automatic location reply. Untouched location-based AI drafts/timers carry an expiry and stop if permission, consent, saved home or the fix changes. Long timers extending past expiry require a fresh reply.
- **Pinned conversations (0.7.24):** Pin conversations at the top of Messages. Hold a chat to pin/unpin. Pins are saved locally across restarts and updates. Pinned and unpinned groups each follow recent message activity; search still filters both. Pinning never changes reply settings or sends a message.
- **New in 0.7.23:** Planning messages alert you and put that conversation on a persistent hold until your own reply is successfully sent. AI drafts cannot make, accept, change or imply plans. Incoming SMS bursts wait for 10 seconds of quiet; the complete unanswered sequence is considered, and newer texts invalidate unfinished replies. More than 50 unanswered messages or an overlong message requires manual review instead of silently dropping context.
- **Fixed in 0.7.22:** Android-compatible Unicode whitespace processing restores conversation opening and reply-length checks; request guards no longer use an Android-unsupported regex flag. A failed conversation load now keeps navigation available and offers Retry. Unicode spacing still counts correctly for history deduplication and the 60-word reply limit.

- **New in 0.7.21 — Random range timers:** choose minimum/maximum whole-minute bounds (for example, 5–30 minutes) in a person's timed auto-send settings, Sleep, the manual timer menu or default timer preferences. The phone selects one random whole-second delay for each reply and retains it with the queued send. Bounds are inclusive, from one minute to seven days, and equal bounds are supported. Sleep never shortens/rerolls a delay that reaches its cutoff; that reply stays unsent for review. Manual ranges show the selected delay before the existing confirmation and begin after acceptance.

- **New in 0.7.20:** the per-person immediate-reply option is now named **Autopilot**. It still sends fresh automatic AI replies without review or an added delay. Existing choices stay saved, and an active Sleep schedule temporarily applies its delay and cutoff.
- Unified timer choices: 1 minute, 5 minutes, 30 minutes, 1 hour, Custom and Random range. Saved nonpreset delays remain custom values instead of being reset.
- One-off Sleep schedule: temporarily use a chosen delay for fresh automatic replies until a selected local cutoff, then pause automatic drafting and sending until explicitly resumed. It preserves each contact's opt-in/delay, never sends unsolicited periodic messages, and leaves manual composition and accepted manual timers alone. Future-message boundaries and persistent session revisions prevent old background work or timers from resuming after settings changes; wall and elapsed deadlines enforce cutoff. Normal history and loop guards still apply.

- AI drafts stay within 360 characters and 60 words. Clear tutorial/code/essay/homework or prompt-override requests are held for owner input. The model is instructed to defer unknown personal answers, credentials and uncertain intent; limits and code/list checks run outside the model as well. Explicit manual composition is unaffected.
- Automatic modes require 20 distinct usable messages for the person, including at least five owner and five incoming examples, with native checks at setup, drafting and sending. Imported per-person logs can supplement recent SMS; known automatic replies do not satisfy the owner's history requirement.
- Import or paste a two-person text chat log in Reply setup. Local parsing confirms speaker identity and previews the latest complete turns within the existing 50-message/8,000-character limits before profile save. Logs remain per person and separate from the SMS provider; importing never starts a model request or enables sending.

- Automatic drafting supports an explicit **No reply needed** decision for a finished conversation or a repeated reply. Whole-message acknowledgments after an outgoing message are filtered locally; context-aware decisions and normalized repeat checks also run in the relay. Saved decisions prevent repeated background requests for the same incoming message. Manual composition remains available, and explicit Draft requests stay reviewable.
- Five consecutive automatic submission attempts to a person pause automation until a manual Reply Pilot send is accepted by the carrier (or delivered). This guard uses persisted send evidence, survives restarts and is checked again before automatic dispatch. It does not silently re-enable on a timeout.

- SMS delivery reports are requested on new sends; chat, inbox and Queue distinguish carrier acceptance from confirmed delivery. All parts of a multipart text must be confirmed. Missing/unsupported reports never become Delivered or Read. RCS and SMS read receipts remain unavailable in this installation; see Settings → Messaging & receipts.

- New Message searches saved contacts by name or phone number, with labeled choices for multiple numbers and a separate Contacts permission control. Contact lookup stays on the phone; selecting a number opens the composer without sending. Manual number entry works without contact access.

- Live inbox updates when a reply starts sending or the carrier reports its result, including the latest preview, time and conversation order, without replacing search or jumping the scrolled list.

- Media opens before attachment reads finish, with a small animated paper-plane loader, a short-lived cache, retry feedback and lazy photo loading. Search focus clears on leaving the field or dismissing its keyboard.
- Tap the send arrow to send immediately; hold it to choose a timer. The visible schedule shortcut and keyboard menu access offer the same timer choices.
- Distinct tinted contact header and reply area frame a deeper chat background, with thin dividers across all ten themes.
- Launcher uses the supplied coral R/reply-arrow artwork on purple. Adaptive layers fit launcher masks; themed icons use the same R shape, and notifications retain their separate transparent white glyph.

- **Tiny Blast** is the default sound for incoming messages and reply drafts/timers. The supplied MP3 is bundled unchanged as `res/raw/tiny_blast.mp3` (about two seconds). New installations and untouched default notification channels use it; existing customized, muted, blocked or privacy-adjusted channels retain their settings. The phone’s global ringtone and notification sound are unchanged.
- Keyboard-aware chat layout: native inset handling keeps keyboard notifications reaching the WebView, while the UI uses the smaller available viewport height and bounds its top offset. Focus, resume and visibility changes refresh the layout without replacing the editor. Mobile chats prevent outer-page scrolling and cap the reply field to the reduced height.
- Compact Messages header, separate search field and native safe-area layout keep app controls clear of the status bar, camera cutout, navigation area and keyboard.
- **Midnight** remains the default navy color theme. All ten palettes use rich dark backgrounds, colored cards, stronger message bubbles and matching controls. Native window and system-bar surfaces follow the selected palette, with light status/navigation icons. Theme previews show each palette.
- On the first open of 0.7.5 or later, the phone app and browser preview select Midnight once. Version 0.7.23 keeps that existing migration flag; a theme chosen after completing it is not reset. Pairing, conversations, drafts, profiles and other settings are preserved. Later theme selections stay saved.
- Smoother typing with coalesced draft saving, lightweight message checks, and an editor that stays in place during background refresh. Rounded native-style controls, system typography and comfortable message text.
- Existing SMS inbox, individual chats with paged SMS/MMS history, contact names, new SMS conversations, and sending/receiving carrier MMS attachments. Local history preparation does not send anything; the separate in-app suggestion setting can request AI context while an enabled chat is open. RCS and group replies are not supported.
- **Try AI**: test OpenAI before changing the default SMS app. No recipient, draft storage or sending is attached to the test flow.
- Clean inbox with search beneath the Messages header, ten saved color themes in Settings, and Reply setup beside the SMS badge.
- Per-person radio-button setup: Off / When I ask / Prepare automatically / Send after timer / Autopilot, relationship type, and saved tone. Relationship dynamics (1,500 characters) and conversation samples (8,000) are optional. Existing notes, samples and opt-ins survive the schema update. New profiles start off; upgrades preserve existing settings and never select Autopilot automatically. Profile changes preserve drafts and manually confirmed messages, and stop automatic timers.
- OpenAI uses up to 50 incoming/sent SMS turns from that conversation when history matching is enabled, plus up to six recent sent texts from that window and up to 12 approved reply examples as writing guidance. Turning matching off omits these style examples and retains eight immediate turns plus explicit profile samples; the complete supported unanswered incoming sequence is retained even when it exceeds eight turns. Older context texts are bounded to 600 characters; displayed messages are not shortened. A bounded in-memory cache prepares enabled contacts locally on app open after SMS access; each actual generation refreshes its context from the phone. Failed, queued and unsent texts are excluded. Incoming messages beyond 1,600 characters require a manual reply. Samples guide each request; they are not permanent model training.
- **Use AI intuition** adapts to the current message, relationship notes and writing samples. Natural, Warm, Brief and Professional remain available as consistent tone choices.
- Autofilled, editable composer with a tap-to-send arrow and long-press timer options for 1 minute, 5 minutes, 30 minutes, 1 hour, and a custom whole-minute delay from 1 minute to 7 days. Manual timers use a native confirmation of recipient, body and delay. The AI service returns text; the phone enforces the saved sending permissions.
- **Autopilot** is a separate per-person choice: send a fresh automatic OpenAI reply as soon as generation completes, with no review step or added delay. It preserves the same permission, freshness and recipient checks, posts the original/reply notification first, then submits directly to the carrier. It needs no exact-alarm access for immediate sends. An active Sleep schedule overrides Autopilot with the chosen delay, requires precise timers, and pauses automatic replies at its cutoff. Model/network/carrier time still applies; explicitly requested drafts remain for review.
- Automatic sending requires an enabled per-person profile, global automation, current draft/conversation, confirmed single SMS recipient, active SIM and Android access. The timer begins after generation and is armed only after its draft notification posts. Editing stops its timer; a manual scheduled reply must be cancelled before editing.
- Expanded notifications show the original incoming message, suggested reply, countdown and Cancel timer action. Lock-screen previews can be disabled; Android controls visibility, expansion size, sound and Do Not Disturb.
- Cancellation before dispatch and a persistent local queue. New incoming messages, reboot/update/time changes and role loss pause sending for review. Stale, late or unauthorized dispatches are rejected. Unknown carrier outcomes never trigger automatic retries.
- All AI features use the paired hosted OpenAI service; on-device model features are removed in 0.10.0.

## Privacy and connection

OpenAI support introduced INTERNET and ACCESS_NETWORK_STATE permissions in version 0.5.0; versions 0.5.1 through 0.7.24 added no permissions. Version 0.8.0 adds optional coarse, precise and background location access. Android location permission is requested only from the location setup button, separately from SMS setup. The WebView remains restricted to packaged assets, with network loads, remote navigation, file access and content access blocked. Native HTTPS requests perform normal certificate and hostname validation and do not follow redirects. The phone’s restricted service token is encrypted using Android Keystore; the OpenAI key stays on the host. App backups are disabled.

Location setup discloses its providers: Android geocoding can receive the entered home address and coordinates; OpenStreetMap Overpass receives coordinates to find nearby named places. Coordinates and the saved home address remain outside OpenAI requests. A fresh coarse label such as “near AutoZone in Cleveland”, “near Cleveland” or “at home” is included only for a current-location question from an allowed person. The public Overpass endpoint is appropriate for this personal prototype, not a scalable commercial dependency; production distribution needs a suitable hosted/commercial map service and its usage/privacy review. Map data © OpenStreetMap contributors, ODbL: https://www.openstreetmap.org/copyright.

Names, phone numbers and contact IDs are not added as AI request fields, but messages, notes, approved examples and images may contain personal details. Only enabled conversations go to OpenAI. Media analysis sends the selected caption/context and up to three resized JPEG images or sampled video frames through the same service; re-encoding removes attachment metadata. Audio is not uploaded for transcription. This may happen through **Understand & reply** or the enabled in-app suggestion setting while the current chat is open. Interpretation can be wrong and remains for review. Per-person context is selected locally and server inputs are bounded and validated. Automated tests use synthetic data and mocked OpenAI; see **VALIDATION.md** for separate live deployment checks.

Version 0.8.4 adds no Android permission or database schema migration. Existing pairing and the hosted `gpt-6-sol` model are unchanged. Local playback does not contact the AI service.

The service disables response storage and does not write conversation content to logs or disk. A short in-memory cache avoids duplicate provider calls. Provider abuse-monitoring retention still applies; see [OpenAI data controls](https://developers.openai.com/api/docs/guides/your-data). Read `relay/README.md` for deployment, limits, rotation and billing controls. A scoped security review and fixes are recorded in **SECURITY-AUDIT.md**. This is not an independent security certification.

## Install and build

Use the versioned installer in `dist/`; see **INSTALL.md**. Quick Share users can transfer the ZIP and extract the APK in Files by Google. Version 0.8.7 uses the non-debuggable release configuration and preserved personal-install signing key from 0.7.12; install over the existing app to retain pairing and profiles. The private development key under `.local-signing/` is excluded from archives. Public production distribution needs a planned production signing strategy.

Build with Android SDK 36, JDK 21 and the included Gradle 8.13 wrapper:

```sh
./gradlew :app:assembleRelease :app:testReleaseUnitTest :app:lintRelease
```

No API key is needed to build or run the tests. To test the service with mocked OpenAI responses:

```sh
cd relay
node --test *.test.mjs
```

For browser checks, serve `app/src/main/assets` on localhost port 8769 and run the scripts under `tools/ui-*.cjs` with Playwright and Chrome. `PLAYWRIGHT_PATH` can reference an existing Playwright install. The native-bridge mocks are clearly labeled and are not real inference. See **VALIDATION.md** for verified checks and the device acceptance checklist.

## Official references

- [OpenAI Responses](https://developers.openai.com/api/docs/guides/text)
- [OpenAI authentication](https://developers.openai.com/api/reference/overview#authentication)
- [ML Kit Smart Reply](https://developers.google.com/ml-kit/language/smart-reply/android)
- [Gemini Nano Prompt API](https://developers.google.com/ml-kit/genai/prompt/android/get-started)
- [Default SMS handlers](https://developer.android.com/guide/topics/permissions/default-handlers)
- [Android alarms](https://developer.android.com/develop/background-work/services/alarms)
- [Android 14 network job permission](https://developer.android.com/about/versions/14/behavior-changes-14#jobscheduler-reinforces-behavior)

MMS parsing uses the Apache-2.0 android-smsmms library. Local AI uses Google ML Kit. This independent preview is not affiliated with Google or OpenAI.
