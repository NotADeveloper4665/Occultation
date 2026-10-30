# Occultation 2.0 Release Checklist

This checklist is a release gate. Keep the GitHub release unpublished until the project owner explicitly approves.

## Automated validation

- [ ] Android unit tests pass
- [ ] Android lint passes
- [ ] Non-root debug APK builds
- [ ] API 29 emulator smoke test passes
- [ ] API 36 emulator smoke test passes
- [ ] No new crash or decoder errors in CI logs

## Physical-device validation

- [ ] Pixel 9a installs and launches the candidate
- [ ] Start a Remote Desktop stream and confirm controls stay hidden over the connecting/loading screen
- [ ] Confirm controls appear after the first actual video frame
- [ ] Confirm Remote Desktop mouse, keyboard, and overlay buttons work
- [ ] Switch into Remote Desktop mode after video is already visible; controls appear immediately
- [ ] Switch away from Remote Desktop mode; controls disappear and do not intercept touches
- [ ] Disconnect/reconnect and verify the overlay waits for the new stream's first frame
- [ ] Test at least one additional Android device / screen size if available

## Publication

- [ ] Owner confirms physical-device testing is successful
- [ ] Final version name/code and release notes reviewed
- [ ] Production signing configuration and upgrade compatibility verified
- [ ] Build and verify signed release APK / AAB
- [ ] Owner explicitly authorizes publication
- [ ] Only then publish the GitHub release and make public distribution changes
