# Makefile for PDF Tools Android App
# Usage: make [target]

# App configuration
APP_PACKAGE := com.aminmart.pdftools
APP_ACTIVITY := .ui.MainActivity
APK_DEBUG := app/build/outputs/apk/debug/app-debug.apk
APK_RELEASE := app/build/outputs/apk/release/app-release-unsigned.apk
APK_RELEASE_SIGNED := app/build/outputs/apk/release/app-release.apk

# Keystore configuration (can be overridden via environment variables)
KEYSTORE_FILE ?= keystore.jks
KEYSTORE_ALIAS ?= pdftools
KEYSTORE_PASS ?= $(shell read -s -p "Enter keystore password: " pass && echo $$pass)
KEY_ALIAS_PASS ?= $(KEYSTORE_PASS)

# ADB command
ADB := adb
ADB_DEVICE := $(shell $(ADB) devices | grep -v "List" | grep "device" | head -1 | cut -f1)

# Colors for output
COLOR_RESET := \033[0m
COLOR_GREEN := \033[32m
COLOR_YELLOW := \033[33m
COLOR_BLUE := \033[34m
COLOR_RED := \033[31m

.PHONY: help build install run uninstall clean devices logs build-release release release-signed keystore

# Default target
help:
	@echo "$(COLOR_BLUE)====================================$(COLOR_RESET)"
	@echo "$(COLOR_GREEN)  PDF Tools Android App - Makefile$(COLOR_RESET)"
	@echo "$(COLOR_BLUE)====================================$(COLOR_RESET)"
	@echo ""
	@echo "$(COLOR_YELLOW)Available targets:$(COLOR_RESET)"
	@echo "  make build          - Build debug APK"
	@echo "  make install        - Install debug APK on device"
	@echo "  make run            - Install and run on device"
	@echo "  make uninstall      - Uninstall app from device"
	@echo "  make clean          - Clean build files"
	@echo "  make rebuild        - Clean and build"
	@echo "  make devices        - List connected devices"
	@echo "  make logs           - Show app logs (logcat)"
	@echo "  make logs-clear     - Clear and show logs"
	@echo "  make shell          - Open ADB shell"
	@echo "  make pull           - Pull app data from device"
	@echo "  make push           - Push APK to device"
	@echo "  make screenshot     - Take screenshot from device"
	@echo "  make screenrecord   - Record screen (Ctrl+C to stop)"
	@echo "  make build-release  - Build unsigned release APK"
	@echo "  make release        - Build and sign release APK"
	@echo "  make release-signed - Build, sign, and install release APK"
	@echo "  make keystore       - Generate new keystore"
	@echo "  make test           - Run unit tests"
	@echo "  make check          - Check connected device"
	@echo ""
	@echo "$(COLOR_YELLOW)Keystore environment variables:$(COLOR_RESET)"
	@echo "  KEYSTORE_FILE  - Keystore file path (default: keystore.jks)"
	@echo "  KEYSTORE_ALIAS - Key alias (default: pdftools)"
	@echo ""
	@echo "$(COLOR_YELLOW)Example:$(COLOR_RESET)"
	@echo "  make release KEYSTORE_FILE=/path/to/keystore.jks KEYSTORE_ALIAS=myalias"
	@echo ""

# Check if device is connected
check:
	@echo "$(COLOR_BLUE)Checking for connected devices...$(COLOR_RESET)"
	@if [ -z "$(ADB_DEVICE)" ]; then \
		echo "$(COLOR_RED)Error: No device connected!$(COLOR_RESET)"; \
		echo "$(COLOR_YELLOW)Please connect a device via USB and enable USB debugging.$(COLOR_RESET)"; \
		echo "$(COLOR_YELLOW)Or run: adb kill-server && adb start-server$(COLOR_RESET)"; \
		exit 1; \
	else \
		echo "$(COLOR_GREEN)Device found: $(ADB_DEVICE)$(COLOR_RESET)"; \
	fi

# List connected devices
devices:
	@echo "$(COLOR_BLUE)Connected devices:$(COLOR_RESET)"
	@$(ADB) devices -l

# Build debug APK
build: check
	@echo "$(COLOR_BLUE)Building debug APK...$(COLOR_RESET)"
	@./gradlew assembleDebug
	@echo "$(COLOR_GREEN)Build complete: $(APK_DEBUG)$(COLOR_RESET)"

# Build release APK
build-release:
	@echo "$(COLOR_BLUE)Building release APK...$(COLOR_RESET)"
	@./gradlew assembleRelease
	@echo "$(COLOR_GREEN)Build complete: $(APK_RELEASE)$(COLOR_RESET)"

# Install APK on device
install: check
	@echo "$(COLOR_BLUE)Installing APK on device $(ADB_DEVICE)...$(COLOR_RESET)"
	@$(ADB) -s $(ADB_DEVICE) install -r $(APK_DEBUG)
	@echo "$(COLOR_GREEN)Installation complete!$(COLOR_RESET)"

# Uninstall app from device
uninstall: check
	@echo "$(COLOR_BLUE)Uninstalling app from device...$(COLOR_RESET)"
	@$(ADB) -s $(ADB_DEVICE) uninstall $(APP_PACKAGE)
	@echo "$(COLOR_GREEN)Uninstallation complete!$(COLOR_RESET)"

# Run app on device
run: check install
	@echo "$(COLOR_BLUE)Starting app...$(COLOR_RESET)"
	@$(ADB) -s $(ADB_DEVICE) shell am start -n $(APP_PACKAGE)/$(APP_ACTIVITY)
	@echo "$(COLOR_GREEN)App started!$(COLOR_RESET)"

# Clean build files
clean:
	@echo "$(COLOR_BLUE)Cleaning build files...$(COLOR_RESET)"
	@./gradlew clean
	@rm -rf app/build
	@rm -rf build
	@rm -rf .gradle
	@rm -rf .idea
	@rm -rf *.iml
	@echo "$(COLOR_GREEN)Clean complete!$(COLOR_RESET)"

# Rebuild (clean + build)
rebuild: clean build

# Show app logs
logs: check
	@echo "$(COLOR_BLUE)Showing logs for $(APP_PACKAGE)...$(COLOR_RESET)"
	@echo "$(COLOR_YELLOW)Press Ctrl+C to stop$(COLOR_RESET)"
	@$(ADB) -s $(ADB_DEVICE) logcat | grep -E "$(APP_PACKAGE)|AndroidRuntime"

# Clear logs and show
logs-clear: check
	@echo "$(COLOR_BLUE)Clearing logs and showing...$(COLOR_RESET)"
	@$(ADB) -s $(ADB_DEVICE) logcat -c
	@$(ADB) -s $(ADB_DEVICE) logcat | grep -E "$(APP_PACKAGE)|AndroidRuntime"

# Open ADB shell
shell: check
	@echo "$(COLOR_BLUE)Opening ADB shell...$(COLOR_RESET)"
	@$(ADB) -s $(ADB_DEVICE) shell

# Pull app data from device
pull: check
	@echo "$(COLOR_BLUE)Pulling app data...$(COLOR_RESET)"
	@mkdir -p ./device-data
	@$(ADB) -s $(ADB_DEVICE) pull /data/data/$(APP_PACKAGE) ./device-data/ 2>/dev/null || \
	$(ADB) -s $(ADB_DEVICE) pull /sdcard/Android/data/$(APP_PACKAGE) ./device-data/
	@echo "$(COLOR_GREEN)Data pulled to ./device-data/$(COLOR_RESET)"

# Push APK to device
push: check
	@echo "$(COLOR_BLUE)Pushing APK to device...$(COLOR_RESET)"
	@$(ADB) -s $(ADB_DEVICE) push $(APK_DEBUG) /sdcard/Download/app-debug.apk
	@echo "$(COLOR_GREEN)APK pushed to /sdcard/Download/app-debug.apk$(COLOR_RESET)"

# Take screenshot
screenshot: check
	@echo "$(COLOR_BLUE)Taking screenshot...$(COLOR_RESET)"
	@$(ADB) -s $(ADB_DEVICE) shell screencap -p /sdcard/screenshot.png
	@$(ADB) -s $(ADB_DEVICE) pull /sdcard/screenshot.png ./screenshot_$(shell date +%Y%m%d_%H%M%S).png
	@$(ADB) -s $(ADB_DEVICE) shell rm /sdcard/screenshot.png
	@echo "$(COLOR_GREEN)Screenshot saved!$(COLOR_RESET)"

# Record screen
screenrecord: check
	@echo "$(COLOR_BLUE)Starting screen recording...$(COLOR_RESET)"
	@echo "$(COLOR_YELLOW)Press Ctrl+C to stop recording$(COLOR_RESET)"
	@$(ADB) -s $(ADB_DEVICE) shell screenrecord /sdcard/recording.mp4
	@$(ADB) -s $(ADB_DEVICE) pull /sdcard/recording.mp4 ./recording_$(shell date +%Y%m%d_%H%M%S).mp4
	@$(ADB) -s $(ADB_DEVICE) shell rm /sdcard/recording.mp4
	@echo "$(COLOR_GREEN)Recording saved!$(COLOR_RESET)"

# Run unit tests
test:
	@echo "$(COLOR_BLUE)Running unit tests...$(COLOR_RESET)"
	@./gradlew test
	@echo "$(COLOR_GREEN)Tests complete!$(COLOR_RESET)"

# Run instrumented tests on device
test-device: check
	@echo "$(COLOR_BLUE)Running instrumented tests on device...$(COLOR_RESET)"
	@./gradlew connectedAndroidTest
	@echo "$(COLOR_GREEN)Tests complete!$(COLOR_RESET)"

# Check app installation status
status: check
	@echo "$(COLOR_BLUE)Checking app status...$(COLOR_RESET)"
	@$(ADB) -s $(ADB_DEVICE) shell pm list packages | grep $(APP_PACKAGE) && \
	echo "$(COLOR_GREEN)App is installed$(COLOR_RESET)" || \
	echo "$(COLOR_YELLOW)App is not installed$(COLOR_RESET)"

# Force stop app
stop: check
	@echo "$(COLOR_BLUE)Force stopping app...$(COLOR_RESET)"
	@$(ADB) -s $(ADB_DEVICE) shell am force-stop $(APP_PACKAGE)
	@echo "$(COLOR_GREEN)App stopped!$(COLOR_RESET)"

# Clear app data
clear-data: check
	@echo "$(COLOR_BLUE)Clearing app data...$(COLOR_RESET)"
	@$(ADB) -s $(ADB_DEVICE) shell pm clear $(APP_PACKAGE)
	@echo "$(COLOR_GREEN)App data cleared!$(COLOR_RESET)"

# Quick run (reinstall and launch)
quick-run: check install
	@echo "$(COLOR_BLUE)Starting app...$(COLOR_RESET)"
	@$(ADB) -s $(ADB_DEVICE) shell am start -n $(APP_PACKAGE)/$(APP_ACTIVITY)
	@echo "$(COLOR_GREEN)App started!$(COLOR_RESET)"

# Debug run (clear data, install, run, show logs)
debug-run: check uninstall install
	@echo "$(COLOR_BLUE)Starting app...$(COLOR_RESET)"
	@$(ADB) -s $(ADB_DEVICE) shell am start -n $(APP_PACKAGE)/$(APP_ACTIVITY)
	@echo "$(COLOR_GREEN)App started!$(COLOR_RESET)"
	@echo ""
	@echo "$(COLOR_YELLOW)Showing logs (Ctrl+C to stop)...$(COLOR_RESET)"
	@sleep 1
	@$(ADB) -s $(ADB_DEVICE) logcat | grep -E "$(APP_PACKAGE)|AndroidRuntime"

# Generate new keystore
keystore:
	@echo "$(COLOR_BLUE)Generating new keystore...$(COLOR_RESET)"
	@echo "$(COLOR_YELLOW)This will create a new keystore file: $(KEYSTORE_FILE)$(COLOR_RESET)"
	@echo ""
	@keytool -genkey -v \
		-keystore $(KEYSTORE_FILE) \
		-alias $(KEYSTORE_ALIAS) \
		-keyalg RSA \
		-keysize 2048 \
		-validity 10000
	@echo ""
	@echo "$(COLOR_GREEN)Keystore created: $(KEYSTORE_FILE)$(COLOR_RESET)"
	@echo "$(COLOR_YELLOW)IMPORTANT: Backup this file securely! You'll need it to publish updates.$(COLOR_RESET)"

# Build unsigned release APK
build-release:
	@echo "$(COLOR_BLUE)Building unsigned release APK...$(COLOR_RESET)"
	@./gradlew assembleRelease
	@echo "$(COLOR_GREEN)Build complete: $(APK_RELEASE)$(COLOR_RESET)"

# Sign release APK
sign-release: build-release
	@echo "$(COLOR_BLUE)Signing release APK...$(COLOR_RESET)"
	@if [ ! -f "$(KEYSTORE_FILE)" ]; then \
		echo "$(COLOR_RED)Error: Keystore file not found: $(KEYSTORE_FILE)$(COLOR_RESET)"; \
		echo "$(COLOR_YELLOW)Generate one with: make keystore$(COLOR_RESET)"; \
		exit 1; \
	fi
	@zipalign -v -p 4 $(APK_RELEASE) $(APK_RELEASE_SIGNED)
	@apksigner sign \
		--ks $(KEYSTORE_FILE) \
		--ks-key-alias $(KEYSTORE_ALIAS) \
		--ks-pass pass:$(KEYSTORE_PASS) \
		--key-pass pass:$(KEY_ALIAS_PASS) \
		$(APK_RELEASE_SIGNED)
	@echo "$(COLOR_GREEN)Signed APK created: $(APK_RELEASE_SIGNED)$(COLOR_RESET)"

# Build and sign release APK
release:
	@echo "$(COLOR_BLUE)Building and signing release APK...$(COLOR_RESET)"
	@if [ ! -f "$(KEYSTORE_FILE)" ]; then \
		echo "$(COLOR_RED)Error: Keystore file not found: $(KEYSTORE_FILE)$(COLOR_RESET)"; \
		echo "$(COLOR_YELLOW)Generate one with: make keystore$(COLOR_RESET)"; \
		exit 1; \
	fi
	@./gradlew assembleRelease
	@zipalign -v -p 4 $(APK_RELEASE) $(APK_RELEASE_SIGNED)
	@apksigner sign \
		--ks $(KEYSTORE_FILE) \
		--ks-key-alias $(KEYSTORE_ALIAS) \
		--ks-pass pass:$(KEYSTORE_PASS) \
		--key-pass pass:$(KEY_ALIAS_PASS) \
		$(APK_RELEASE_SIGNED)
	@echo "$(COLOR_GREEN)Release APK ready: $(APK_RELEASE_SIGNED)$(COLOR_RESET)"
	@echo ""
	@echo "$(COLOR_YELLOW)APK Info:$(COLOR_RESET)"
	@ls -lh $(APK_RELEASE_SIGNED)
	@echo ""
	@apksigner verify --print-certs $(APK_RELEASE_SIGNED) 2>/dev/null || true

# Build, sign, and install release APK on device
release-signed: check release
	@echo "$(COLOR_BLUE)Installing signed release APK on device $(ADB_DEVICE)...$(COLOR_RESET)"
	@$(ADB) -s $(ADB_DEVICE) install -r $(APK_RELEASE_SIGNED)
	@echo "$(COLOR_GREEN)Installation complete!$(COLOR_RESET)"

# Verify APK signature
verify-apk:
	@echo "$(COLOR_BLUE)Verifying APK signature...$(COLOR_RESET)"
	@if [ -f "$(APK_RELEASE_SIGNED)" ]; then \
		apksigner verify --verbose $(APK_RELEASE_SIGNED); \
	elif [ -f "$(APK_RELEASE)" ]; then \
		echo "$(COLOR_YELLOW)Signed APK not found, checking unsigned...$(COLOR_RESET)"; \
		echo "$(COLOR_RED)APK is not signed!$(COLOR_RESET)"; \
	else \
		echo "$(COLOR_RED)No APK found. Run 'make build-release' first.$(COLOR_RESET)"; \
	fi

# Install release APK on device
install-release: check
	@echo "$(COLOR_BLUE)Installing release APK on device...$(COLOR_RESET)"
	@if [ -f "$(APK_RELEASE_SIGNED)" ]; then \
		$(ADB) -s $(ADB_DEVICE) install -r $(APK_RELEASE_SIGNED); \
	elif [ -f "$(APK_RELEASE)" ]; then \
		echo "$(COLOR_YELLOW)Signed APK not found, installing unsigned...$(COLOR_RESET)"; \
		$(ADB) -s $(ADB_DEVICE) install -r $(APK_RELEASE); \
	else \
		echo "$(COLOR_RED)No APK found. Run 'make build-release' first.$(COLOR_RESET)"; \
		exit 1; \
	fi
	@echo "$(COLOR_GREEN)Installation complete!$(COLOR_RESET)"
