#!/home/sungo/Documents/termux-app-dev/tools/.venv/bin/python3
"""
Claude History HTTP Server with Turso Integration
Watches JSONL files and automatically syncs to Turso database.

Usage:
    python3 claude-history-server-with-turso.py [port]

Environment variables:
    TURSO_DB_URL - Turso database URL (e.g., libsql://xxx.turso.io)
    TURSO_AUTH_TOKEN - Turso authentication token

Default port: 8765
"""

import os
import sys
import json
import time
import threading
from http.server import HTTPServer, BaseHTTPRequestHandler
from pathlib import Path
from urllib.parse import unquote, urlparse
from datetime import datetime

try:
    from watchdog.observers import Observer
    from watchdog.events import FileSystemEventHandler
    WATCHDOG_AVAILABLE = True
except ImportError:
    WATCHDOG_AVAILABLE = False
    print("Warning: watchdog not installed. File monitoring disabled.")
    print("Install with: pip install watchdog")

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

# Default port
PORT = 8766

# Claude projects directory
CLAUDE_PROJECTS_DIR = os.path.expanduser("~/.claude/projects")

# Turso configuration
TURSO_DB_URL = os.getenv("TURSO_DB_URL", "")
TURSO_AUTH_TOKEN = os.getenv("TURSO_AUTH_TOKEN", "")


def get_project_dir_name(cwd: str) -> str:
    """Convert current working directory to Claude project directory name."""
    path = cwd.lstrip("/").replace("/", "-")
    return f"-{path}"


# ============================================================================
# Turso Client
# ============================================================================

class TursoClient:
    """Client for Turso HTTP API."""

    def __init__(self, db_url: str, auth_token: str):
        # Handle libsql:// scheme
        if db_url.startswith("libsql://"):
            db_url = db_url.replace("libsql://", "https://")
        elif not db_url.startswith("http://") and not db_url.startswith("https://"):
            db_url = "https://" + db_url

        # Ensure URL ends with /v2/pipeline
        if db_url.endswith("/"):
            db_url = db_url[:-1]
        if not db_url.endswith("/v2/pipeline"):
            db_url = f"{db_url}/v2/pipeline"

        self.db_url = db_url
        self.auth_token = f"Bearer {auth_token}"

        # Setup session with retry
        self.session = requests.Session()
        retry = Retry(total=3, backoff_factor=1, status_forcelist=[500, 502, 503, 504])
        adapter = HTTPAdapter(max_retries=retry)
        self.session.mount("http://", adapter)
        self.session.mount("https://", adapter)

    def execute(self, sql: str, args=None):
        """Execute SQL statement."""
        if args is None:
            args = []

        # Convert Python types to Turso API format
        def convert_value(v):
            """Convert Python value to Turso API format."""
            if v is None:
                # Convert None to empty string for TEXT columns
                return {"type": "text", "value": ""}
            elif isinstance(v, str):
                return {"type": "text", "value": v}
            elif isinstance(v, int):
                return {"type": "integer", "value": str(v)}
            elif isinstance(v, float):
                return {"type": "float", "value": str(v)}
            elif isinstance(v, bool):
                return {"type": "integer", "value": str(1 if v else 0)}
            else:
                return {"type": "text", "value": str(v)}

        turso_args = [convert_value(arg) for arg in args]

        request_data = {
            "requests": [
                {
                    "type": "execute",
                    "stmt": {
                        "sql": sql,
                        "args": turso_args
                    }
                },
                {
                    "type": "close"
                }
            ]
        }

        headers = {
            "Authorization": self.auth_token,
            "Content-Type": "application/json"
        }

        try:
            response = self.session.post(
                self.db_url,
                json=request_data,
                headers=headers,
                timeout=30
            )

            # Debug logging
            print(f"[Turso] Request: {sql[:100]}...")
            print(f"[Turso] Response status: {response.status_code}")

            if response.status_code != 200:
                print(f"[Turso] Response body: {response.text[:500]}")

            response.raise_for_status()
            data = response.json()

            if data.get("results") and len(data["results"]) > 0:
                result = data["results"][0]
                if result.get("type") == "ok" and result.get("response"):
                    return result["response"].get("result")
                elif result.get("type") == "error" and result.get("error"):
                    raise Exception(f"Turso error: {result['error'].get('message')}")

            return None
        except Exception as e:
            print(f"[Turso] Error executing SQL: {e}")
            import traceback
            print(f"[Turso] Traceback: {traceback.format_exc()}")
            raise

    def initialize_db(self):
        """Initialize database tables."""
        print("[Turso] Initializing database...")

        # Table 1: raw_jsonl_lines
        self.execute("""
            CREATE TABLE IF NOT EXISTS raw_jsonl_lines (
                session_id TEXT NOT NULL,
                line_no INTEGER NOT NULL,
                ts TEXT,
                uuid TEXT,
                type TEXT,
                raw_json TEXT NOT NULL,
                PRIMARY KEY (session_id, line_no)
            )
        """)

        self.execute("""
            CREATE INDEX IF NOT EXISTS idx_raw_session_ts
            ON raw_jsonl_lines(session_id, ts)
        """)

        # Table 2: turns
        self.execute("""
            CREATE TABLE IF NOT EXISTS turns (
                turn_id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL,
                user_uuid TEXT NOT NULL,
                ts TEXT,
                cwd TEXT,
                git_branch TEXT,
                user_text TEXT NOT NULL,
                UNIQUE(session_id, user_uuid)
            )
        """)

        self.execute("""
            CREATE INDEX IF NOT EXISTS idx_turns_session_ts
            ON turns(session_id, ts)
        """)

        # Table 3: assistant_texts
        self.execute("""
            CREATE TABLE IF NOT EXISTS assistant_texts (
                assistant_text_id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL,
                turn_id INTEGER,
                assistant_uuid TEXT,
                parent_uuid TEXT,
                ts TEXT,
                model TEXT,
                stop_reason TEXT,
                part_index INTEGER NOT NULL,
                text TEXT NOT NULL,
                FOREIGN KEY(turn_id) REFERENCES turns(turn_id) ON DELETE SET NULL
            )
        """)

        self.execute("""
            CREATE INDEX IF NOT EXISTS idx_asst_turn_order
            ON assistant_texts(turn_id, assistant_text_id)
        """)

        print("[Turso] Database initialized")

    def sync_jsonl_file(self, file_path: str, session_id: str):
        """Sync JSONL file to Turso."""
        print(f"[Turso] Syncing {file_path}...")

        try:
            # Get max line number
            result = self.execute(
                "SELECT MAX(line_no) as max_line FROM raw_jsonl_lines WHERE session_id = ?",
                [session_id]
            )
            start_line = 0
            if result and result.get("rows") and len(result["rows"]) > 0:
                start_line = self._val_as_int(result["rows"][0][0])

            # Fetch UUID map
            uuid_to_turn_id = {}
            result = self.execute(
                "SELECT user_uuid, turn_id FROM turns WHERE session_id = ?",
                [session_id]
            )
            if result and result.get("rows"):
                for row in result["rows"]:
                    uuid = self._val_as_str(row[0])
                    turn_id = self._val_as_int(row[1])
                    if uuid:
                        uuid_to_turn_id[uuid] = turn_id

            # Process file
            with open(file_path, 'r') as f:
                for line_no, line in enumerate(f, start=1):
                    if line_no <= start_line:
                        continue
                    if not line.strip():
                        continue

                    data = json.loads(line)
                    uuid = data.get("uuid")
                    msg_type = data.get("type")
                    ts = data.get("created_at") or data.get("timestamp")

                    # Insert raw line
                    self.execute(
                        """INSERT OR REPLACE INTO raw_jsonl_lines
                           (session_id, line_no, ts, uuid, type, raw_json)
                           VALUES (?, ?, ?, ?, ?, ?)""",
                        [session_id, line_no, ts, uuid, msg_type, line]
                    )

                    # Process user message
                    if msg_type == "user":
                        message = data.get("message", {})
                        text = self._extract_text(message)

                        if text:
                            cwd = data.get("cwd")
                            git_branch = data.get("gitBranch") or data.get("git_branch")

                            # Fallback to metadata
                            metadata = data.get("metadata", {})
                            if not cwd:
                                cwd = metadata.get("cwd")
                            if not git_branch:
                                git_branch = metadata.get("gitBranch") or metadata.get("git_branch")

                            # Insert turn
                            if uuid not in uuid_to_turn_id:
                                self.execute(
                                    """INSERT OR REPLACE INTO turns
                                       (session_id, user_uuid, ts, cwd, git_branch, user_text)
                                       VALUES (?, ?, ?, ?, ?, ?)""",
                                    [session_id, uuid, ts, cwd, git_branch, text]
                                )

                                # Get inserted turn_id
                                result = self.execute(
                                    "SELECT turn_id FROM turns WHERE session_id = ? AND user_uuid = ?",
                                    [session_id, uuid]
                                )
                                if result and result.get("rows"):
                                    uuid_to_turn_id[uuid] = self._val_as_int(result["rows"][0][0])

                    # Process assistant message
                    elif msg_type == "assistant":
                        turn_id = uuid_to_turn_id.get(uuid)

                        if turn_id:
                            assistant_uuid = data.get("uuid")
                            parent_uuid = data.get("in_response_to") or data.get("parent_uuid")
                            model = data.get("model")
                            stop_reason = data.get("stop_reason")

                            content = data.get("content", [])
                            if isinstance(content, list):
                                for part_idx, part in enumerate(content, start=1):
                                    if part.get("type") == "text":
                                        text = part.get("text", "")
                                        if text:
                                            self.execute(
                                                """INSERT INTO assistant_texts
                                                   (session_id, turn_id, assistant_uuid, parent_uuid,
                                                    ts, model, stop_reason, part_index, text)
                                                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                                                [session_id, turn_id, assistant_uuid, parent_uuid,
                                                 ts, model, stop_reason, part_idx, text]
                                            )
                            elif isinstance(content, str):
                                self.execute(
                                    """INSERT INTO assistant_texts
                                       (session_id, turn_id, assistant_uuid, parent_uuid,
                                        ts, model, stop_reason, part_index, text)
                                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                                    [session_id, turn_id, uuid, None,
                                     ts, model, stop_reason, 1, content]
                                )

            print(f"[Turso] Synced {file_path} successfully")

        except Exception as e:
            print(f"[Turso] Error syncing {file_path}: {e}")
            raise

    def _extract_text(self, message: dict) -> str:
        """Extract text content from message."""
        content = message.get("content")
        if isinstance(content, str):
            return content
        elif isinstance(content, list):
            for part in content:
                if part.get("type") == "text":
                    return part.get("text", "")
                elif "content" in part and isinstance(part["content"], str):
                    return part["content"]
        return ""

    @staticmethod
    def _extract_value(val):
        """Extract value from Turso response format.

        Turso returns values in format: {"type": "text|integer", "value": "..."}
        or as primitive values for backward compatibility.

        Ported from Java's valToString()/valAsLong() methods.
        """
        if val is None:
            return None
        elif isinstance(val, dict):
            # Typed value format: {"type": "...", "value": "..."}
            if "value" in val:
                return val["value"]
            else:
                return val
        else:
            # Primitive value (string, int, etc.)
            return val

    @staticmethod
    def _val_as_int(val) -> int:
        """Extract integer value from Turso response format."""
        extracted = TursoClient._extract_value(val)
        if extracted is None:
            return 0
        try:
            return int(extracted)
        except (ValueError, TypeError):
            return 0

    @staticmethod
    def _val_as_str(val) -> str:
        """Extract string value from Turso response format."""
        extracted = TursoClient._extract_value(val)
        if extracted is None:
            return None
        return str(extracted)


# ============================================================================
# File Watcher
# ============================================================================

class JsonlFileHandler(FileSystemEventHandler):
    """Handler for JSONL file changes."""

    def __init__(self, turso_client: TursoClient):
        super().__init__()
        self.turso_client = turso_client
        self.last_sync_time = {}
        self.sync_debounce = 2.0  # seconds

    def on_modified(self, event):
        """Called when a file is modified."""
        if event.is_directory:
            return

        file_path = event.src_path
        if not file_path.endswith(".jsonl"):
            return

        # Skip agent files
        if os.path.basename(file_path).startswith("agent"):
            return

        # Debounce: avoid syncing too frequently
        now = time.time()
        if file_path in self.last_sync_time:
            if now - self.last_sync_time[file_path] < self.sync_debounce:
                return

        self.last_sync_time[file_path] = now

        # Get session_id from filename
        session_id = os.path.basename(file_path)

        print(f"[Watcher] File modified: {file_path}")

        try:
            self.turso_client.sync_jsonl_file(file_path, session_id)
        except Exception as e:
            print(f"[Watcher] Sync failed: {e}")


def start_file_watcher(turso_client: TursoClient):
    """Start file watcher for JSONL files."""
    if not WATCHDOG_AVAILABLE:
        print("[Watcher] File watching disabled (watchdog not installed)")
        return None

    if not os.path.exists(CLAUDE_PROJECTS_DIR):
        print(f"[Watcher] Projects directory not found: {CLAUDE_PROJECTS_DIR}")
        return None

    event_handler = JsonlFileHandler(turso_client)
    observer = Observer()
    observer.schedule(event_handler, CLAUDE_PROJECTS_DIR, recursive=True)
    observer.start()

    print(f"[Watcher] Watching {CLAUDE_PROJECTS_DIR}")

    return observer


# ============================================================================
# HTTP Server
# ============================================================================

class ClaudeHistoryHandler(BaseHTTPRequestHandler):

    def log_message(self, format, *args):
        """Custom log format."""
        print(f"[{self.log_date_time_string()}] {args[0]}")

    def send_json(self, data, status=200):
        """Send JSON response."""
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(json.dumps(data).encode())

    def do_GET(self):
        path = unquote(self.path)

        # GET /health - Health check
        if path == "/health":
            self.send_json({
                "status": "ok",
                "cwd": os.getcwd(),
                "turso_configured": bool(TURSO_DB_URL and TURSO_AUTH_TOKEN),
                "watcher_active": WATCHDOG_AVAILABLE
            })

        else:
            self.send_json({
                "error": "Unknown endpoint",
                "endpoints": [
                    "GET /health - Health check"
                ]
            }, 404)


# ============================================================================
# Main
# ============================================================================

def main():
    port = PORT
    if len(sys.argv) > 1:
        try:
            port = int(sys.argv[1])
        except ValueError:
            print(f"Invalid port: {sys.argv[1]}")
            sys.exit(1)

    print("=" * 60)
    print("Claude History Server with Turso Integration")
    print("=" * 60)
    print(f"Port: {port}")
    print(f"CWD: {os.getcwd()}")
    print(f"Project dir: {CLAUDE_PROJECTS_DIR}")
    print(f"Turso configured: {bool(TURSO_DB_URL and TURSO_AUTH_TOKEN)}")
    print(f"Watchdog available: {WATCHDOG_AVAILABLE}")
    print("")
    print("Endpoints:")
    print(f"  GET /health - Health check")
    print("")
    print("Press Ctrl+C to stop")
    print("=" * 60)

    # Initialize Turso client
    turso_client = None
    observer = None

    if TURSO_DB_URL and TURSO_AUTH_TOKEN:
        try:
            turso_client = TursoClient(TURSO_DB_URL, TURSO_AUTH_TOKEN)
            turso_client.initialize_db()
            observer = start_file_watcher(turso_client)
        except Exception as e:
            print(f"[Error] Failed to initialize Turso: {e}")
            print("Continuing without Turso integration...")
    else:
        print("[Info] TURSO_DB_URL and TURSO_AUTH_TOKEN not set")
        print("Set environment variables to enable Turso integration")

    # Start HTTP server
    server = HTTPServer(("0.0.0.0", port), ClaudeHistoryHandler)

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down...")
        if observer:
            observer.stop()
            observer.join()
        server.shutdown()


if __name__ == "__main__":
    main()
