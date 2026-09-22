# Resonance

An Android audio field recorder and harmonic scratchpad built with Kotlin, Jetpack Compose, AudioRecord, and Room. The application requests microphone permission only and stores WAV audio in its internal storage.

## Startup

The 2.6-second intro draws orbiting blue, rose, and purple gradients, an iridescent title, and a spring-animated credits capsule. It crossfades into the recorder over 450 ms. API 31+ uses isolated blurred graphics layers; older devices retain the gradient and tinted scrim. System-disabled animations receive a static intro. Saved activity state avoids replay after rotation once the intro finishes.

Made by Nitir, Arsh, GPT-5.6 Terra and Gemini 3.8 Flash

## Build

Use JDK 17, Gradle 8.9, and Android SDK 35. Open this folder in Android Studio, or run `gradle :app:assembleDebug`. CI builds the debug APK on GitHub. Release builds require your own signing configuration before distribution.

## Status

Source implementation is in progress. Physical-device audio, accessibility, and graphics verification are still required before a production release. Pitch is a single-note estimate and BPM is a heuristic, not a full musical-key analysis. The existing transcriptSnippet field currently stores a musical analysis summary, not speech transcription.
