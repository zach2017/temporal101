"""
Minimal HTTP upload server for testing URL-type outputs.
Accepts PUT/POST → saves to /uploads/. Also serves GET for download testing.
Pure stdlib — no dependencies.
"""
import os, sys
from http.server import HTTPServer, BaseHTTPRequestHandler

UPLOAD_DIR = "/uploads"
os.makedirs(UPLOAD_DIR, exist_ok=True)

class UploadHandler(BaseHTTPRequestHandler):
    def do_PUT(self):  self._save()
    def do_POST(self): self._save()

    def _save(self):
        content_length = int(self.headers.get("Content-Length", 0))
        filename = os.path.basename(self.path) or "upload.bin"
        filepath = os.path.join(UPLOAD_DIR, filename)
        received = 0
        with open(filepath, "wb") as f:
            if content_length > 0:
                while received < content_length:
                    chunk = self.rfile.read(min(65536, content_length - received))
                    if not chunk: break
                    f.write(chunk); received += len(chunk)
            else:
                while True:
                    chunk = self.rfile.read(65536)
                    if not chunk: break
                    f.write(chunk); received += len(chunk)
        size = os.path.getsize(filepath)
        body = f'{{"status":"ok","file":"{filename}","bytes":{size}}}\n'.encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)
        print(f"Received {filename}: {size} bytes", flush=True)

    def do_GET(self):
        filename = os.path.basename(self.path)
        filepath = os.path.join(UPLOAD_DIR, filename)
        if not os.path.exists(filepath):
            self.send_response(404); self.end_headers(); return
        size = os.path.getsize(filepath)
        self.send_response(200)
        self.send_header("Content-Length", str(size))
        self.send_header("Content-Type", "application/octet-stream")
        self.end_headers()
        with open(filepath, "rb") as f:
            while chunk := f.read(65536):
                self.wfile.write(chunk)

if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
    server = HTTPServer(("0.0.0.0", port), UploadHandler)
    print(f"Upload server listening on :{port}", flush=True)
    server.serve_forever()
