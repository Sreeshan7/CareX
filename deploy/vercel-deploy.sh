#!/usr/bin/env bash
# Optional split deployment: React SPA on Vercel, API on Railway/Render.
# Vercel cannot run the Spring Boot backend (long-running JVM + scheduler) or host PostgreSQL, so the SPA
# proxies /api/* to the backend. Because the browser only ever talks to the Vercel origin, no CORS is needed.
#
# Usage:  BACKEND_URL=https://carex-leave.up.railway.app VERCEL_TOKEN=... ./deploy/vercel-deploy.sh
set -euo pipefail
: "${BACKEND_URL:?set BACKEND_URL to the public https URL of the backend}"
: "${VERCEL_TOKEN:?set VERCEL_TOKEN}"
cd "$(dirname "$0")/../frontend"

node -e '
const backend = process.env.BACKEND_URL.replace(/\/$/, "");
const csp = "default-src '\''self'\''; script-src '\''self'\''; style-src '\''self'\'' '\''unsafe-inline'\''; img-src '\''self'\'' data: blob:; media-src '\''self'\'' blob: data:; connect-src '\''self'\''; frame-ancestors '\''none'\''";
const cfg = {
  framework: "vite",
  buildCommand: "npm run build",
  outputDirectory: "dist",
  rewrites: [
    { source: "/api/:path*", destination: `${backend}/api/:path*` },
    { source: "/actuator/health", destination: `${backend}/actuator/health` },
    { source: "/((?!assets/|favicon.svg).*)", destination: "/index.html" }
  ],
  headers: [{ source: "/(.*)", headers: [
    { key: "Content-Security-Policy", value: csp },
    { key: "X-Frame-Options", value: "DENY" },
    { key: "X-Content-Type-Options", value: "nosniff" },
    { key: "Referrer-Policy", value: "no-referrer" },
    { key: "Permissions-Policy", value: "microphone=(self), camera=(), geolocation=()" }
  ]}]
};
require("fs").writeFileSync("vercel.json", JSON.stringify(cfg, null, 2));
console.log("vercel.json written, /api ->", backend);
'
npx --yes vercel@latest deploy --prod --yes --token "$VERCEL_TOKEN" --name carex-leave
