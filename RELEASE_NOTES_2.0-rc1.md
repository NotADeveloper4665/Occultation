# Occultation 2.0 Release Candidate 1

> Pre-release build for device testing. This is not the public 2.0 release.

## Highlights

- Delay the Remote Desktop touch-control overlay until Android confirms that the first decoded video frame has rendered to the stream surface. The loading screen is no longer covered by desktop controls.
- Keep Remote Desktop input routing initialized during connection setup; only the visual overlay is deferred.
- Show the controls immediately if Remote Desktop mode is selected after the first video frame is already visible.
- Add unit coverage for the first-frame visibility policy.
- Continue the Occultation 1.9 input-routing, focus-cleanup, scaled-coordinate, and in-stream controls work.

## Platform

- Minimum Android: 10 (API 29)
- Target / compile SDK: 36
- Version: 2.0-rc1 (version code 11)

## Validation status

CI builds, unit tests, lint, and emulator smoke tests must pass before device testing. Pixel and other physical-device validation remains required. This candidate is not considered release-ready until the project owner confirms that manual testing succeeds.

## Known release gate

Do not publish this candidate as a public GitHub release or distribute it as stable 2.0 until the owner explicitly approves after testing.
