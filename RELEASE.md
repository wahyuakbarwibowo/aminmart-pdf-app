# Release Build Guide

## Quick Start

### First Time Setup

1. **Generate a keystore** (keep it secure!):
```bash
make keystore
# or manually:
keytool -genkey -v -keystore keystore.jks -alias pdftools -keyalg RSA -keysize 2048 -validity 10000
```

2. **Create keystore.properties** (optional, for Gradle signing):
```bash
cp keystore.properties.template keystore.properties
# Edit keystore.properties with your actual credentials
```

### Build & Sign Release APK

**Option 1: Using Makefile (Recommended)**
```bash
# Build and sign release APK
make release

# Build, sign, and install on device
make release-signed

# Build, sign, install and run
make release-signed && make run
```

**Option 2: Using Gradle with properties file**
```bash
# Create keystore.properties first, then:
./gradlew assembleRelease
```

**Option 3: Using environment variables**
```bash
export KEYSTORE_FILE=keystore.jks
export KEYSTORE_ALIAS=pdftools
export KEYSTORE_PASS=your_password
export KEY_ALIAS_PASS=your_password

./gradlew assembleRelease
```

**Option 4: Using command line parameters**
```bash
./gradlew assembleRelease \
  -PSTORE_FILE=../keystore.jks \
  -PSTORE_PASSWORD=your_password \
  -PKEY_ALIAS=pdftools \
  -PKEY_PASSWORD=your_password
```

## Makefile Commands

| Command | Description |
|---------|-------------|
| `make keystore` | Generate new keystore file |
| `make build-release` | Build unsigned release APK |
| `make release` | Build and sign release APK |
| `make release-signed` | Build, sign, and install on device |
| `make sign-release` | Sign existing unsigned APK |
| `make verify-apk` | Verify APK signature |
| `make install-release` | Install release APK on device |

## Output Files

- **Unsigned APK**: `app/build/outputs/apk/release/app-release-unsigned.apk`
- **Signed APK**: `app/build/outputs/apk/release/app-release.apk`

## Verify APK

```bash
# Verify signature
make verify-apk

# Or manually
apksigner verify --verbose app/build/outputs/apk/release/app-release.apk

# Check APK info
zipinfo -l app/build/outputs/apk/release/app-release.apk
```

## Install on Device

```bash
# Install signed release APK
make install-release

# Or manually
adb install -r app/build/outputs/apk/release/app-release.apk
```

## Security Notes

⚠️ **IMPORTANT:**
- Never commit `keystore.jks` or `keystore.properties` to version control
- Keep backups of your keystore in a secure location
- You'll need the same keystore to publish updates to Google Play
- If you lose the keystore, you cannot update your app on Play Store

## Troubleshooting

### "Keystore file not found"
```bash
# Generate a new keystore
make keystore

# Or specify custom path
make release KEYSTORE_FILE=/path/to/keystore.jks
```

### "zipalign not found"
```bash
# Install Android Build Tools or use full path
export PATH=$PATH:$ANDROID_HOME/build-tools/34.0.0/
```

### "apksigner not found"
```bash
# Install Android Build Tools or use full path
export PATH=$PATH:$ANDROID_HOME/build-tools/34.0.0/
```

### Build fails with signing error
```bash
# Check keystore credentials
# Try with explicit parameters:
make release KEYSTORE_FILE=keystore.jks KEYSTORE_ALIAS=pdftools
```

## Google Play Store Upload

1. Build signed release APK:
```bash
make release
```

2. The APK at `app/build/outputs/apk/release/app-release.apk` is ready for upload

3. For Android App Bundle (AAB) format:
```bash
./gradlew bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab
```
