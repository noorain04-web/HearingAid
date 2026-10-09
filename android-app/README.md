# HearLink Native Android bridge

This native proof-of-concept requests the routing combination the browser app cannot guarantee:
- Capture uses AudioRecord with a preferred TYPE_BUILTIN_MIC input.
- Playback uses AudioTrack with USAGE_MEDIA, requesting a Bluetooth A2DP output when Android exposes one.
- Audio processing is a PCM loop with adjustable gain and hard sample clipping.

## Build
GitHub Actions builds a debug APK and uploads it as the hearlink-native-debug-apk artifact. Open the workflow run and download the artifact to install on the test phone.

## Test on iQOO Z11x 5G + boAt Rockerz 255 Pro+
1. Connect the neckband.
2. In Bluetooth settings, keep Media audio ON and initially set Call audio OFF.
3. Launch HearLink Native and grant microphone permission.
4. Tap Refresh Bluetooth outputs and check the selected device.
5. Set gain low and tap Start phone mic → Bluetooth. Speak near the phone and watch the input level.
6. Confirm sound comes from the neckband. Stop immediately if feedback or uncomfortable loudness occurs.

If the neckband is not listed, confirm it is connected and Media audio is enabled. This app intentionally does not enable communication mode or Bluetooth SCO, because those paths commonly select the headset microphone.

## Android routing caveat
AudioRecord.setPreferredDevice() and AudioTrack.setPreferredDevice() are routing preferences, not universal guarantees. Vendor firmware and Bluetooth policy may override them. The app reports requested devices, but the input meter does not prove physical source identity. Compare speaking near the phone and near the neckband microphone.

This is an experimental utility, not a hearing aid or medical device. The gain control is not calibrated and there is no validated feedback-prevention system. Start at low gain and stop if sound is uncomfortable.
