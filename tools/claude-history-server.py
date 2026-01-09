#!/usr/bin/env python3
"""
Claude History HTTP Server
Simple HTTP server to serve Claude conversation history files.

Usage:
    python3 claude-history-server.py [port]
    
Default port: 8765
"""

import os
import sys
import json
from http.server import HTTPServer, BaseHTTPRequestHandler
from pathlib import Path
from urllib.parse import unquote

# Default port
PORT = 8765

# Claude projects directory
CLAUDE_PROJECTS_DIR = os.path.expanduser("~/.claude/projects")


def get_project_dir_name(cwd: str) -> str:
    """Convert current working directory to Claude project directory name."""
    # Remove leading slash and replace remaining slashes with dashes
    # Then prepend with dash
    path = cwd.lstrip("/").replace("/", "-")
    return f"-{path}"


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
    
    def send_file(self, filepath):
        """Send file content."""
        try:
            with open(filepath, "rb") as f:
                content = f.read()
            self.send_response(200)
            self.send_header("Content-Type", "application/octet-stream")
            self.send_header("Content-Length", len(content))
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(content)
        except FileNotFoundError:
            self.send_json({"error": "File not found"}, 404)
        except Exception as e:
            self.send_json({"error": str(e)}, 500)
    
    def do_GET(self):
        path = unquote(self.path)
        
        # GET /api/projects - List all project directories
        if path == "/api/projects":
            self.handle_list_projects()
        
        # GET /api/files?project=xxx - List files in a project
        elif path.startswith("/api/files?project="):
            project = path.split("project=")[1]
            self.handle_list_files(project)
        
        # GET /api/files/current - List files for current working directory
        elif path == "/api/files/current":
            self.handle_list_current_files()
        
        # GET /api/download?file=xxx - Download a specific file
        elif path.startswith("/api/download?file="):
            filepath = path.split("file=")[1]
            self.handle_download(filepath)
        
        # GET /api/latest - Get latest conversation file from current project
        elif path == "/api/latest":
            self.handle_latest()
        
        # GET /health - Health check
        elif path == "/health":
            self.send_json({"status": "ok", "cwd": os.getcwd()})
        
        else:
            self.send_json({
                "error": "Unknown endpoint",
                "endpoints": [
                    "GET /health - Health check",
                    "GET /api/projects - List all projects",
                    "GET /api/files?project=xxx - List files in project",
                    "GET /api/files/current - List files for current directory",
                    "GET /api/latest - Download latest file from current project",
                    "GET /api/download?file=xxx - Download specific file"
                ]
            }, 404)
    
    def handle_list_projects(self):
        """List all project directories."""
        try:
            if not os.path.exists(CLAUDE_PROJECTS_DIR):
                self.send_json({"error": "Claude projects directory not found"}, 404)
                return
            
            projects = []
            for name in os.listdir(CLAUDE_PROJECTS_DIR):
                full_path = os.path.join(CLAUDE_PROJECTS_DIR, name)
                if os.path.isdir(full_path):
                    projects.append({
                        "name": name,
                        "path": full_path
                    })
            
            self.send_json({"projects": projects})
        except Exception as e:
            self.send_json({"error": str(e)}, 500)
    
    def handle_list_files(self, project_name):
        """List conversation files in a project."""
        try:
            project_path = os.path.join(CLAUDE_PROJECTS_DIR, project_name)
            if not os.path.exists(project_path):
                self.send_json({"error": f"Project not found: {project_name}"}, 404)
                return
            
            files = self._get_jsonl_files(project_path)
            self.send_json({"project": project_name, "files": files})
        except Exception as e:
            self.send_json({"error": str(e)}, 500)
    
    def handle_list_current_files(self):
        """List files for current working directory's project."""
        try:
            cwd = os.getcwd()
            project_name = get_project_dir_name(cwd)
            project_path = os.path.join(CLAUDE_PROJECTS_DIR, project_name)
            
            if not os.path.exists(project_path):
                self.send_json({
                    "error": f"Project not found for cwd",
                    "cwd": cwd,
                    "expected_project": project_name,
                    "expected_path": project_path
                }, 404)
                return
            
            files = self._get_jsonl_files(project_path)
            self.send_json({
                "cwd": cwd,
                "project": project_name,
                "files": files
            })
        except Exception as e:
            self.send_json({"error": str(e)}, 500)
    
    def handle_latest(self):
        """Download the latest conversation file from current project."""
        try:
            cwd = os.getcwd()
            project_name = get_project_dir_name(cwd)
            project_path = os.path.join(CLAUDE_PROJECTS_DIR, project_name)
            
            if not os.path.exists(project_path):
                self.send_json({
                    "error": f"Project not found for cwd",
                    "cwd": cwd,
                    "expected_project": project_name
                }, 404)
                return
            
            files = self._get_jsonl_files(project_path)
            if not files:
                self.send_json({"error": "No conversation files found"}, 404)
                return
            
            # Files are already sorted by mtime desc, first is latest
            latest = files[0]
            self.send_file(latest["path"])
        except Exception as e:
            self.send_json({"error": str(e)}, 500)
    
    def handle_download(self, filepath):
        """Download a specific file."""
        # Security: only allow files under CLAUDE_PROJECTS_DIR
        filepath = unquote(filepath)
        abs_path = os.path.abspath(filepath)
        
        if not abs_path.startswith(CLAUDE_PROJECTS_DIR):
            self.send_json({"error": "Access denied: file outside projects directory"}, 403)
            return
        
        self.send_file(abs_path)
    
    def _get_jsonl_files(self, directory):
        """Get list of .jsonl files in directory, sorted by mtime desc."""
        files = []
        for f in Path(directory).glob("*.jsonl"):
            # Skip agent files
            if f.name.startswith("agent"):
                continue
            
            stat = f.stat()
            files.append({
                "name": f.name,
                "path": str(f),
                "size": stat.st_size,
                "mtime": stat.st_mtime
            })
        
        # Sort by modification time, newest first
        files.sort(key=lambda x: x["mtime"], reverse=True)
        return files


def main():
    port = PORT
    if len(sys.argv) > 1:
        try:
            port = int(sys.argv[1])
        except ValueError:
            print(f"Invalid port: {sys.argv[1]}")
            sys.exit(1)
    
    print(f"=" * 50)
    print(f"Claude History Server")
    print(f"=" * 50)
    print(f"Port: {port}")
    print(f"CWD: {os.getcwd()}")
    print(f"Project dir: {CLAUDE_PROJECTS_DIR}")
    print(f"")
    print(f"Endpoints:")
    print(f"  GET /health              - Health check")
    print(f"  GET /api/projects        - List all projects")
    print(f"  GET /api/files/current   - List files for CWD")
    print(f"  GET /api/latest          - Download latest file")
    print(f"  GET /api/download?file=x - Download specific file")
    print(f"")
    print(f"Press Ctrl+C to stop")
    print(f"=" * 50)
    
    server = HTTPServer(("0.0.0.0", port), ClaudeHistoryHandler)
    
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down...")
        server.shutdown()


if __name__ == "__main__":
    main()
