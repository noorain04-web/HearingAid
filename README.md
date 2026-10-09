# HearLink Unified Audio Lab

A single-page prototype consolidating the four workflows identified in the supplied Phone as Bluetooth Microphone PDF.

- **A — HearLink Remote Microphone:** microphone capture, optional high-pass/presence EQ, dynamic compressor, gain control, and live input meter.
- **B — HearLink ANSD Edition:** experimental signal-chain controls using the same audio engine. Not a validated ANSD-specific algorithm or clinical treatment.
- **C — HearLink PRO Audiogram Interface:** right/left threshold entry at 250–8000 Hz and CSV export. It does not calculate a hearing-aid prescription.
- **D — HearLink PA Hybrid Audio Bridge:** select an available microphone and permitted output device where browser and operating-system support allow.

## Run it

1. Serve this page over HTTPS (GitHub Pages is suitable).
2. Open it in a supported browser and grant microphone permission.
3. Select a microphone and, if supported, an audio output device.
4. Start with a low output level. Stop immediately if feedback, discomfort, or painful loudness occurs.
5. Test each phone/headset/hearing-aid combination separately.

## Important technical limits

- The app cannot force a Bluetooth profile (such as HFP/SCO or A2DP), turn off a headset microphone at kernel level, or control a hearing aid's proprietary DSP.
- Microphone capture requests the selected input when exposed by the browser, but device IDs and routing vary by OS/browser. Verify the actual input in practice.
- AudioContext output selection is not supported in all browsers; the operating system may continue to choose the output route.
- The interactive latency hint is only a preference. The displayed baseLatency is not total end-to-end latency and excludes Bluetooth, OS, and transducer delays.
- A high-pass filter/EQ may change timbre but cannot eliminate bone-conducted own voice. Feedback prevention requires device-specific acoustic testing and cannot be guaranteed by this code.
- Noise suppression and echo cancellation are browser/OS processing requests, not guaranteed algorithms. This prototype does not include neural-network speech separation, binaural beamforming, verified feedback cancellation, or validated ANSD temporal processing.
- Complete removal of environmental sound is neither guaranteed nor a safe design target; important alarms and warning sounds must remain audible.

## Source-derived design choices

The PDF includes earlier Remote Microphone versions using a low-latency Web Audio context, browser echo/noise constraints, high-pass filtering, presence EQ around 3 kHz, compression, a live visualizer, audiogram input, and a PA hybrid routing concept. This prototype consolidates these into a shared audio graph and mode selector while avoiding claims that browser code can force hardware routing or deliver clinical-grade outcomes.

## Status

Prototype only. Not a medical device, not clinically validated, and not a replacement for prescribed hearing aids or professional audiological fitting. Clinical and device testing is required before any real-world therapeutic use.
