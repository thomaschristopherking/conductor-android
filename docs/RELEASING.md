# Make a release

The workflow `.github/workflows/release.yml` builds the APK of each release. It signs the APK with the release key, and attaches it to the release as `conductor-android.apk`. Every published APK has the same signature, so Android installs each new build as an update. You keep the app data, the API key and the GitHub token.

## Set up the release key one time

Do these steps on your own computer. Do not make the key in a cloud workspace, because then the private key is on that machine.

1. Make the keystore. `keytool` asks for a password. Use the same password for the store and for the key.

   ```bash
   keytool -genkeypair -v -keystore conductor-release.jks -alias conductor -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Conductor for Android"
   ```

2. Get the SHA-256 fingerprint of the certificate.

   ```bash
   keytool -list -v -keystore conductor-release.jks -alias conductor | grep 'SHA256:'
   ```

3. Add the four secrets and the variable to the repository.

   ```bash
   base64 -i conductor-release.jks | gh secret set RELEASE_KEYSTORE_BASE64
   gh secret set RELEASE_STORE_PASSWORD
   gh secret set RELEASE_KEY_PASSWORD
   gh secret set RELEASE_KEY_ALIAS --body conductor
   gh variable set RELEASE_CERT_SHA256 --body "<the fingerprint from step 2>"
   ```

4. Keep a copy of `conductor-release.jks` and its password in a password manager.

> **Warning:** do not lose the keystore. Without it, no build can update the installed app, and every phone must uninstall the app again.

The workflow stops when a secret or the variable is missing. It also stops when the APK certificate is not the one in `RELEASE_CERT_SHA256`.

## Publish a release

1. Merge the changes into `main`.
2. Create a release on GitHub. Use a tag that ends with the next build number, for example `v0.1.0-release.14`. Attach the screenshots, but do not attach an APK.

   ```bash
   gh release create v0.1.0-release.14 --target main --prerelease --title "Conductor for Android 0.1.0 (build 14)" --notes-file notes.md
   ```

3. The workflow starts when the release is published. After about 5 minutes, `conductor-android.apk` is on the release.

The build number of the tag becomes the `versionCode` of the APK. Android does not install a build with a lower `versionCode` over a higher one, so each tag must have a higher build number than the last one.

To build the APK again for an existing release, run the workflow by hand:

```bash
gh workflow run release.yml -f tag=v0.1.0-release.14
```

## Change from a debug-signed build

Builds up to 13 have the debug key of the machine that built them. Uninstall such a build one last time before you install the first build with the release key.
