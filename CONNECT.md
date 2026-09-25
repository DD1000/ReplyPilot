# Connect your installed Reply Pilot app

The Railway service is deployed, the OpenAI key is configured, and a real synthetic test reply succeeded using gpt-6-sol on September 23, 2026. The user has paired the phone and reports successful test replies. Install version 0.6.0 as an update to add the per-person reply setup form; pairing and key setup are retained. The steps below are for a fresh phone connection.

## OpenAI key setup (completed)

Open your [Reply Pilot project](https://railway.com/project/f8a2a0b4-7952-46fd-bbb8-e09669a07f09). Select **reply-pilot-api → Variables → New Variable**.

Use **OPENAI_API_KEY** as the variable name. Paste the key into its value field in Railway, then save and deploy the staged change. Do not paste the key in this chat. The other required settings, including the separate phone credential, are already configured.

If needed, create a dedicated key in the [OpenAI API dashboard](https://platform.openai.com/api-keys) and enable API billing. ChatGPT subscription billing is separate. The key needs access to Responses and the configured model, gpt-6-sol.

## Pair the Pixel

Open the private `phone-pairing.txt` file under `.local-secrets/` in this project. Copy its complete contents to **Reply Pilot → Settings → Phone pairing code → Save connection**. This code gives access to paid drafts, so keep it private. It is a separate credential from your OpenAI key.

Tap **Check connection**, then **Try AI → OpenAI → Generate test reply**. A real draft has already verified server-side model access and billing; this checks the connection from your phone. Once the test works, choose When I ask or Prepare automatically in each intended person’s Reply setup form. Every send still requires your approval.
