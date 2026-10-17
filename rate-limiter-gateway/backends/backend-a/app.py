"""
Dummy upstream service A.

Deliberately dependency-free (stdlib only) so it builds instantly in Docker.
Supports fault injection via env vars so you can actually trip the gateway's
circuit breaker during a demo instead of just trusting it works:

  FAILURE_RATE   - float 0..1, probability a request returns 500 (default 0)
  LATENCY_MS     - artificial delay added to every response (default 0)
"""
import json
import os
import random
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

SERVICE_NAME = "backend-a"
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

    def do_GET(self):
        if LATENCY_MS:
            time.sleep(LATENCY_MS / 1000.0)

        if self.path == "/health":
            return self._respond(200, {"status": "UP", "service": SERVICE_NAME})

        if random.random() < FAILURE_RATE:
            return self._respond(500, {"error": "simulated failure", "service": SERVICE_NAME})

        return self._respond(200, {
            "service": SERVICE_NAME,
            "path": self.path,
            "message": f"Hello from {SERVICE_NAME}",
        })

    def do_POST(self):
        self.do_GET()

    def log_message(self, fmt, *args):
        pass  # keep container logs quiet


if __name__ == "__main__":
    port = int(os.environ.get("PORT", "9001"))
    server = ThreadingHTTPServer(("0.0.0.0", port), Handler)
    print(f"{SERVICE_NAME} listening on :{port} (failure_rate={FAILURE_RATE}, latency_ms={LATENCY_MS})")
    server.serve_forever()
