# Termux UI Terminology

This document defines the terminology for the different UI control areas in the Termux application.

## 1. App Function Buttons (機能ボタン)
*   **Description**: Native Android buttons fixed to the layout (usually colorful).
*   **Purpose**: Trigger high-level application features and GUI dialogs.
*   **Examples**: `DIR` (Directory Navigation), `Sessions` (Byobu Management).
*   **Location**: Fixed, typically above the terminal view.

## 2. Extra Keys (拡張キーボード)
*   **Description**: A scrollable/swipeable strip of buttons (usually gray).
*   **Purpose**: Emulate keyboard keys, send text macros, or control terminal input.
*   **Examples**: `ESC`, `Ctrl`, `TAB`, `claude` macro.
*   **Location**: Bottom of the screen, above the designated keyboard area. Configurable via `termux.properties`.

## Usage Distinction
-   **Function Buttons**: Use for complex app logic, opening dialogs, or changing app state.
-   **Extra Keys**: Use for text input shortcuts and standard keyboard emulation.
