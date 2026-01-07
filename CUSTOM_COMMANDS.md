# Custom Commands Specification

This document describes the format and usage of the Custom Commands features in Termux.

## Overview
Custom commands allow users to define and save frequently used shell commands for quick access. These commands are stored in a JSON format and can be organized into folders.

## Data Structure

### Command Object
Each custom command is represented by a JSON object with the following properties:

| Property | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `id` | String | Yes | Unique identifier (UUID recommended) |
| `name` | String | Yes | Display name of the command |
| `command` | String | Yes | The shell command to execute |
| `folderId` | String | No | ID of the parent folder (null for root) |
| `order` | Integer | No | Sort order (default: 0) |

### JSON Format Example
The export file format is a JSON array containing command objects.

```json
[
  {
    "id": "c1458e0a-3d2b-4b2a-8c1d-9e6f3a5b0c7d",
    "name": "Check Disk Space",
    "command": "df -h",
    "folderId": null,
    "order": 0
  },
  {
    "id": "a2569d1b-4e3c-5c3b-9d2e-0f7a4b6c1d8e",
    "name": "Update Packages",
    "command": "pkg update && pkg upgrade",
    "folderId": "f9876c2d-1a4b-2c3d-4e5f-6a7b8c9d0e1f",
    "order": 1
  }
]
```

## Import/Export
- **Export**: Generates a `.json` file containing all saved commands.
- **Import**: Reads a `.json` file and merges the commands into the current list. Duplicate IDs may be overwritten or skipped depending on implementation.

## Usage
1. Open the "Custom Commands" menu from the side drawer or toolbar.
2. Click "Add Command" to create a new entry.
3. Long-press a command to Edit or Delete.
