# Upstream baseline

Occultation is based on **Artemide**, https://github.com/derflacco/moonlight-android.

Pinned starting commit: `8157555264bf1b77eb76261755805cdbba26c3c2`.

Artemide itself derives from Artemis and Moonlight Android. Preserve copyright
notices and GPL licensing. Keep Java namespace `com.limelight` to avoid disrupting
JNI and resource bindings; installation IDs are independent Occultation IDs.

The starting commit includes MediaTek decoder adjustments, lower-delay frame
release logic, settings profiles, and the lightweight performance overlay.
Other experimental upstream branches may differ. Do not silently change the
baseline to Artemis or assume unreleased branch features are present.
