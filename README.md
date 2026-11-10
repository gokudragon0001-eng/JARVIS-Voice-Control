# J.A.R.V.I.S. — Local AI + Voice Phone Control

An Android Studio Kotlin project with a JARVIS-inspired interface and custom cyan reactor logo. The AI chat model runs on-device; no OpenAI API key or cloud chatbot is used.

## Voice commands included
Tap the microphone once, speak a command, and JARVIS submits the recognized speech automatically:

- `Open WhatsApp`
- `Search Ali chat`
- `Search Ali chat and send message hi`
- `Ali ko WhatsApp per call milao`
- `Open JARVIS`

Contact commands use the names and phone numbers saved in the phone's Contacts app. If the contact name matches more than one person, JARVIS asks you to say the full name.

## Important WhatsApp setup
1. Install WhatsApp and save your contacts with their correct phone numbers, including country code.
2. Open JARVIS and allow Microphone permission.
3. Give Contacts permission when Android asks after the first contact command.
4. Tap **ENABLE ASSIST** in JARVIS. Android Accessibility settings open.
5. Find **JARVIS WhatsApp Assist** and enable it. Android will show a system warning because accessibility can inspect and interact with on-screen controls. Enable it only if you trust this app and understand the permission.
6. Return to JARVIS. Tap the microphone and speak a command.

The optional accessibility helper attempts to tap WhatsApp's Send or Voice call control for the one action you requested. After a successful tap, it speaks a completion message. WhatsApp changes its screen labels and layout between versions, so this UI automation is best-effort and must be tested on your phone. Check the recipient and message in WhatsApp during initial tests. If the helper cannot find the right control, finish the action manually and disable the accessibility service if you do not want it.

## Important Android limitations
- This version is **tap-to-talk**. Android does not allow an ordinary app to continuously listen for a custom wake phrase such as “Hey JARVIS” while closed/backgrounded without a separate foreground microphone service, visible notification, battery exemptions, and device-specific testing. Open the app and tap its mic first.
- Speech recognition is provided by the phone's speech-recognition service. Roman Urdu recognition quality and offline availability vary by device and installed language packs. English commands are the most predictable. You can also type commands.
- WhatsApp chat opening uses the phone contact's number and the `wa.me` link. WhatsApp may show a confirmation screen or require you to choose an app.
- The accessibility helper is not an official WhatsApp API. It may need updates if WhatsApp changes button labels. It does not bypass WhatsApp security or login.
- A WhatsApp call can be launched only when WhatsApp and the device allow the relevant UI action. Verify the call is going to the intended contact.

## Local AI model (separate download)
The `.litertlm` model is not included in the ZIP because it is hundreds of MB. You can continue using the local AI chat by downloading a compatible model:
- https://huggingface.co/zendevvv/odoko-gemma3-1b
- https://huggingface.co/litert-community/Gemma3-1B-IT

Select the `.litertlm` file in the app using **SELECT MODEL**. The AI model is not needed for the built-in WhatsApp commands; those commands are handled directly on the phone.

## Build
1. Extract the ZIP and open the `JARVIS_Local_AI_Android` folder in Android Studio.
2. Use JDK 17 and install Android SDK Platform 35 if prompted.
3. Let Gradle Sync download dependencies (internet required on the development computer).
4. Run on a real Android phone, then follow the WhatsApp setup above.

## Project notes
- Kotlin + Jetpack Compose
- LiteRT-LM Android runtime `0.16.1` for local AI chat
- Android speech recognition and Text-to-Speech
- `VoiceCommandProcessor.kt` handles built-in voice commands without needing the LLM
- `WhatsAppAssistService.kt` is the optional accessibility helper
- `res/drawable/ic_jarvis_logo.xml` is the JARVIS reactor-style app logo

The source project is provided for Android Studio. It has not been device-tested against your specific phone or WhatsApp version; run it and share any Android Studio build error or phone screenshot so the code can be adjusted accurately.
