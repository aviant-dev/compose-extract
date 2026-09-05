# Compose Extract - Jetpack Compose Refactoring Plugin

An Android Studio and IntelliJ IDEA plugin designed to automate architectural Jetpack Compose refactoring, focusing on composable component extraction, state hoisting, and automated parameter/import resolution.

---

## Features

* **Instant Composable Extraction**: Highlight any Compose UI block and extract it into a decoupled `@Composable` component using `Cmd + Option + E` (macOS) / `Ctrl + Alt + E` (Windows/Linux) or `Alt + Enter` (Context Action).
* **Smart Scope & Visibility Resolution**: Automatically applies the appropriate visibility modifier based on the destination:
    * **Same File**: `private` (keeps UI component encapsulated).
    * **New File (Same Module)**: `internal` (module-level sharing).
    * **New File (External/Design System Module)**: `public` (cross-module access).
* **Interactive Refactoring Dialog**: Built with Kotlin UI DSL v2 featuring real-time `PascalCase` function name validation, target module selection with an IDE-native tree directory picker, and `Modifier` parameter toggles.
* **Pure AST Mutation Engine**: Safe code mutation using Kotlin PSI (`KtPsiFactory`):
    * Auto-detects external variables and hoists them into parameters.
    * Standardizes parameter ordering (`Required -> Modifier -> Lambda`).
    * Injects `Modifier` chaining (`modifier.then(...)`) into the root layout element.
    * Automatically resolves and injects missing imports (`@Composable`, `Modifier`, Data Classes).
* **Atomic Multi-File Operations**: All refactoring actions (creating files, updating call-sites, adjusting imports) are wrapped in a single transaction, supporting complete single-step Undo/Redo (`Cmd + Z`).

---

## Compatibility

| Component | Minimum Supported Version | Reason / Requirement |
| :--- | :--- | :--- |
| **Android Studio** | Koala (2024.1.1+) / Build 241+ | Uses IntelliJ Platform SDK refactoring pipelines |
| **Kotlin Compiler** | K1 and K2 | K1 and K2 modes are declared supported |
| **JDK** | Java JDK 21 | Use the version bundled with the supported IDE; it must be enabled |
| **Jetpack Compose** | 1.6.0+ | Supports modern `Modifier.Node` layout constructs |

---

## Usage Guide

1. Open any Kotlin file containing Jetpack Compose code.
2. Highlight the UI code block you want to extract.
3. Trigger the extraction dialog through any of these methods:
    * **Keyboard Shortcut**: `Cmd + Option + E` (macOS) / `Ctrl + Alt + E` (Windows/Linux).
    * **Refactor Menu**: Right-click selection -> **Refactor** -> **Composable...**
    * **Floating Toolbar / Quick Fix**: Press `Alt + Enter` -> Select **Extract Composable Component**.
4. Configure your component in the Refactoring Dialog:
    * Enter the **Composable Name** (must start with an uppercase letter).
    * Choose **Target Destination** (`Same File` or `New File`).
    * Select target directory using the native IDE Tree Picker.
    * Toggle `Include Modifier parameter` as needed.
5. Press **OK** or hit `Enter` to execute the transformation.

---

## Installation

### Install from ZIP (Local Build)

1. Download the latest `compose-extract-x.x.x.zip` release from the Releases tab.
2. Open **Android Studio**.
3. Go to **Settings/Preferences** -> **Plugins**.
4. Click the gear icon in the top-right corner and select **Install Plugin from Disk...**.
5. Select the downloaded `.zip` file and restart the IDE.

---

## Building from Source

To build and test the plugin locally:

```bash
# Clone the repository
git clone https://github.com/aviant-dev/compose-extract.git
cd compose-extract

# Run Sandboxed Android Studio / IntelliJ instance with plugin pre-loaded
./gradlew runIde

# Build distribution ZIP package
./gradlew buildPlugin
```

The output file will be generated at `build/distributions/compose-extract-x.x.x.zip`.

---

## Running Tests

Execute the unit test suite covering AST mutations, selection analysis, and dialog validations:

```bash
./gradlew test
```

---

## License

This project is licensed under the MIT License — see the LICENSE file for details.