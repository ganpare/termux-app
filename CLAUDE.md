# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a fork of [termux/termux-app](https://github.com/termux/termux-app) with custom UI features for SSH connection management, byobu session control, and custom command execution. The app is an Android terminal emulator with a full Linux environment.

## Build Commands

### Development Build
```bash
# Build debug APK (downloads bootstraps automatically)
./gradlew assembleDebug

# Debug APK output location:
# app/build/outputs/apk/debug/termux-app_apt-android-7-debug_universal.apk
```

### Testing
```bash
# Run all unit tests
./gradlew test

# Run tests for specific module
./gradlew :terminal-emulator:test
./gradlew :app:testDebugUnitTest

# Run lint checks
./gradlew lint
```

### Clean Build
```bash
# Clean all build artifacts (including downloaded bootstrap zips)
./gradlew clean
```

### Package Variants
The app supports two bootstrap variants via `TERMUX_PACKAGE_VARIANT` environment variable:
- `apt-android-7` (default) - For Android 7+
- `apt-android-5` - For Android 5-6 (legacy, no package update support)

## Module Architecture

### Four Gradle Modules

**app** - Main application module
- Contains activities, services, UI dialogs
- Custom fork features: SSH, Byobu, Custom Commands, Directory Navigation
- Depends on: `terminal-view`, `termux-shared`

**terminal-view** - Terminal UI rendering layer
- `TerminalView.java` - Custom View for rendering terminal
- `TerminalRenderer.java` - Text/canvas rendering with font/color management
- Handles touch input, gestures, text selection
- Depends on: `terminal-emulator`

**terminal-emulator** - Terminal emulation engine
- `TerminalSession.java` - Shell process + terminal I/O
- `TerminalEmulator.java` - VT100/xterm emulation, ANSI escape sequence processing
- JNI layer for subprocess/PTY management (C code in `app/src/main/cpp/`)
- Standalone library with no dependencies

**termux-shared** - Shared utilities library
- `TermuxConstants.java` - All hardcoded paths and constants (see Forking section)
- Shared preferences, file utils, shell utils, logger
- General utilities used across modules

### UI Terminology (see UI_TERMINOLOGY.md)

**Function Buttons** - Native Android buttons (colorful) in left drawer
- Trigger app features via dialogs
- Examples: SESSIONS (blue), CUSTOM (orange), SSH (green), HELP (purple)

**Extra Keys** - Scrollable gray button strip at bottom
- Keyboard key emulation and text macros
- Configured via `termux.properties`
- Current macros: `claude`, `claude -r`, `cursor-agent`, `codex`, `gemini`, `exit`

## Custom Fork Features Architecture

### SSH Connection Management
**Location:** `app/src/main/java/com/termux/app/ssh/`

**Data Flow:**
1. `SshConfigManager.java` - Persists configs to SharedPreferences as JSON
2. `SshConnectionConfig.java` - Data model with `buildSshCommand()` method
3. `SshKeyManager.java` - Manages SSH private key files
4. UI: `SshConnectionsActivity.java` + `res/layout/dialog_ssh_connection.xml`

**Storage:** SharedPreferences key `ssh_connections` (JSON array)

**Security Note:** SSH passwords stored in plain text (not encrypted)

### Byobu Session Integration
**Location:** `app/src/main/java/com/termux/app/byobu/`

**Components:**
- `ByobuSessionManager.java` - Session operations via terminal scraping
  - Uses markers `__BYOBU_SESS_START_7x9K2mN__` / `__BYOBU_SESS_END_7x9K2mN__`
  - Parses terminal transcript to extract session names
  - Handles nested session prevention (auto-detach from tmux)
- `ByobuCommandHelper.java` - Command reference library with categories

**Key Methods:**
- `listSessions(TerminalSession, callback)` - Async session list via terminal scraping
- `parseSessionList(transcript)` - Extract session names from terminal buffer
- `attachToSession(session, name)` - Send attach command with nesting check

**Limitation:** Terminal scraping is fragile, depends on byobu output format

### Custom Commands
**Location:** `app/src/main/java/com/termux/app/customcmd/`

**Data Model:**
- `CustomCommand.java` - Command: id, name, command, folderId, order
- `CommandFolder.java` - Folder: id, name, order
- `CustomCommandManager.java` - CRUD operations + Import/Export

**Import Modes:**
- `MERGE` - Add new only, skip duplicate IDs
- `REPLACE` - Add new and overwrite duplicates
- `CLEAR_AND_IMPORT` - Clear existing data first

**Storage:** SharedPreferences `custom_commands_prefs` (JSON arrays)

**Export Format:** JSON with version field for future compatibility (see CUSTOM_COMMANDS.md)

### Directory Navigation
**Location:** `app/src/main/java/com/termux/app/dirnav/`
- Terminal scraping for directory listings

### Claude Conversation Sync
**Location:** `app/src/main/java/com/termux/app/claude/`
- `ConversationSyncManager.java` - Syncs terminal conversations
- `ClaudeChatParser.java` - Parses agent comments from JSONL history

## Key File Locations

### Main Application Entry Points
- `app/src/main/java/com/termux/app/TermuxActivity.java` - Main activity, initializes all function buttons
- `app/src/main/java/com/termux/app/TermuxService.java` - Background service for session management
- `app/src/main/res/layout/activity_termux.xml` - Main layout (DrawerLayout with TerminalView)

### Terminal I/O Flow
```
User Input → TerminalView → TermuxTerminalViewClient
          → TerminalSession.write() → subprocess stdin

Subprocess output → TerminalSession IO queue → TerminalEmulator (ANSI processing)
                  → TerminalBuffer → TerminalView.invalidate() → TerminalRenderer
```

### Session Clients (Glue Layer)
- `app/src/main/java/com/termux/app/terminal/TermuxTerminalSessionActivityClient.java`
- `app/src/main/java/com/termux/app/terminal/TermuxTerminalViewClient.java`
- `app/src/main/java/com/termux/app/terminal/io/TermuxTerminalExtraKeys.java`

### Configuration Files
- `gradle.properties` - Gradle configuration
- `app/build.gradle` - App module build config, bootstrap downloads
- `termux.properties` (runtime, not in repo) - User terminal settings

## Development Patterns

### Adding Custom Features
1. Create package under `app/src/main/java/com/termux/app/<feature>/`
2. Data model POJOs with `toJson()` / `fromJson()` methods
3. Manager class for business logic + SharedPreferences persistence
4. Add function button in `TermuxActivity.onCreate()`
5. Create dialog layouts in `app/src/main/res/layout/`

### Data Persistence
All custom data uses SharedPreferences with JSON serialization:
```java
SharedPreferences prefs = SharedPreferenceUtils.getPrivateSharedPreferences(context);
prefs.edit().putString("key", jsonArray.toString()).apply();
```

### Terminal Interaction
Send commands to active terminal session:
```java
TerminalSession session = mTermService.getTerminalSessions().get(currentIndex);
session.write("command\n".getBytes(StandardCharsets.UTF_8));
```

Read terminal transcript (for scraping):
```java
String transcript = ShellUtils.getTerminalSessionTranscriptText(session, false, false);
```

### Adding Extra Keys Macros
Edit `termux.properties` (user config file, not tracked in git):
```
extra-keys = [['ESC','/','-','HOME','UP','END','PGUP','DEL'], \
              ['TAB','CTRL','ALT','LEFT','DOWN','RIGHT','PGDN','BKSP']]
```

Add macro buttons via code in extra keys configuration parser.

## Testing Strategy

### Unit Tests Location
- `app/src/test/java/` - App-level tests
- `terminal-emulator/src/test/java/` - Terminal emulation tests (extensive)
- `termux-shared/src/main/java/com/termux/shared/file/tests/` - File utils tests

### Running Specific Tests
```bash
# Single test class
./gradlew :terminal-emulator:test --tests TerminalTest

# Test package
./gradlew :app:test --tests com.termux.app.claude.*
```

### Robolectric Tests
App tests use Robolectric (Android mocking framework):
```java
@RunWith(RobolectricTestRunner.class)
public class TermuxActivityTest { ... }
```

## Forking / Package Name Changes

To fork with different package name, see `termux-shared/src/main/java/com/termux/shared/termux/TermuxConstants.java` javadocs.

**Required steps:**
1. Update constants in `TermuxConstants.java`
2. Recompile bootstrap zip for new package name
3. Update plugin apps (not all use `TermuxConstants` yet)
4. Change `manifestPlaceholders.TERMUX_PACKAGE_NAME` in `app/build.gradle`

See README.md "Forking" section for details.

## Commit Message Format

Use Conventional Commits spec with **capitalized type and description**:

```
Added: Add SSH connection password encryption
Fixed: Fix byobu session parsing for multi-word names
Changed!: Change custom command storage to SQLite (breaking change)
```

**Valid types:** Added, Changed, Deprecated, Removed, Fixed, Security

Space after colon is required. Use `!` before colon for breaking changes.

## Important Constraints

### Security
- Avoid command injection when building SSH commands (use proper escaping in `buildSshCommand()`)
- SSH passwords currently stored plain text - use Android Keystore for production
- Validate user input before executing terminal commands

### Terminal Scraping
When scraping terminal output:
- Use unique markers (like byobu manager does)
- Add delay before reading (terminal output is async)
- Parse defensively (output format may vary)

### Module Boundaries
- **Never** hardcode paths - use `TermuxConstants` from `termux-shared`
- Keep terminal emulation logic in `terminal-emulator` module
- Keep Android-specific code out of `terminal-emulator` (it's a standalone library)

### SharedPreferences
- Use `SharedPreferenceUtils.getPrivateSharedPreferences()` for sensitive data
- Always check for null when deserializing JSON
- Handle import ID conflicts properly (see `CustomCommandManager.importCommands()`)

## Bootstrap Downloads

Bootstrap zips are automatically downloaded during build via `downloadBootstrap()` task.

**Manual download trigger:**
```bash
./gradlew downloadBootstraps
```

**Cache location:** `app/src/main/cpp/bootstrap-*.zip`

**Cleanup:** Bootstraps are deleted on `./gradlew clean`

## Useful Documentation References

- [Termux Wiki](https://wiki.termux.com/wiki/)
- [Terminal Emulator Implementation Guide](https://invisible-island.net/xterm/ctlseqs/ctlseqs.html)
- UI terminology: `UI_TERMINOLOGY.md`
- Custom commands spec: `CUSTOM_COMMANDS.md`
