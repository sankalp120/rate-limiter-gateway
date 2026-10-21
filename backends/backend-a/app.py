"""
Dummy upstream service. Stdlib only, no installs needed.
Run:  python app.py
Env vars (optional, used later to test the circuit breaker):
  PORT          default 9001
  SERVICE_NAME  default backend-a
  FAILURE_RATE  0..1, chance of returning 500 (default 0)
  LATENCY_MS    artificial delay per request (default 0)
"""
import json
import os
import random
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PORT = int(os.environ.get("PORT", "9001"))
SERVICE_NAME = os.environ.get("SERVICE_NAME", "backend-a")
FAILURE_RATE = float(os.environ.get("FAILURE_RATE", "0"))
LATENCY_MS = int(os.environ.get("LATENCY_MS", "0"))


class Handler(BaseHTTPRequestHandler):
    def _respond(self, status, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _handle(self):
        if LATENCY_MS:
            time.sleep(LATENCY_MS / 1000.0)
        if random.random() < FAILURE_RATE:
            return self._respond(500, {"error": "simulated failure", "service": SERVICE_NAME})
        self._respond(200, {"service": SERVICE_NAME, "path": self.path, "status": "UP"})

    do_GET = do_POST = do_PUT = do_DELETE = _handle

    def log_message(self, fmt, *args):
        print(f"[{SERVICE_NAME}] {self.command} {self.path}")


if __name__ == "__main__":
    print(f"{SERVICE_NAME} listening on http://localhost:{PORT} "
          f"(failure_rate={FAILURE_RATE}, latency_ms={LATENCY_MS})")
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
