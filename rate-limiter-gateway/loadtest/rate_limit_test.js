// Load test for the rate-limiter gateway.
// Run with: k6 run rate_limit_test.js
// Or override target: k6 run -e BASE_URL=http://localhost:8080 -e API_KEY=demo-key rate_limit_test.js
//
// Demo tenant "demo-key" is seeded at 20 req/60s (token bucket) by default,
// so this script intentionally exceeds that to show 429s + Retry-After.

import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const API_KEY = __ENV.API_KEY || "demo-key";

const allowed = new Counter("requests_allowed");
const rejected = new Counter("requests_rejected");
const rejectionRate = new Rate("rejection_rate");

export const options = {
  scenarios: {
    burst: {
      executor: "constant-arrival-rate",
      rate: 50,           // 50 requests/sec, well above the seeded 20/60s limit
      timeUnit: "1s",
      duration: "30s",
      preAllocatedVUs: 20,
      maxVUs: 100,
    },
  },
  thresholds: {
    // We EXPECT a meaningful rejection rate -- that's the point of the test.
    rejection_rate: ["rate>0.1"],
  },
};

export default function () {
  const res = http.get(`${BASE_URL}/api/a/health`, {
    headers: { "X-API-Key": API_KEY },
  });

  const wasAllowed = res.status === 200;
  const wasRejected = res.status === 429;

  check(res, {
    "status is 200 or 429": (r) => r.status === 200 || r.status === 429,
    "has rate limit headers": (r) => r.headers["X-Ratelimit-Limit"] !== undefined,
  });

  allowed.add(wasAllowed ? 1 : 0);
  rejected.add(wasRejected ? 1 : 0);
  rejectionRate.add(wasRejected);

  sleep(0.05);
}
