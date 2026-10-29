import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { request as httpRequest } from "node:http";

// The ZeroWatch API deliberately requires a known Content-Length on event
// ingestion. Some browser/proxy combinations forward the JSON request as a
// chunked stream, which the API rejects with HTTP 403. Buffer only this small,
// bounded demo endpoint and forward it with an explicit length.
function zeroWatchEventProxy() {
  return {
    name: "zerotrace-zerowatch-content-length-proxy",
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const pathname = (req.url || "").split("?", 1)[0];
        if (req.method !== "POST" || pathname !== "/api/watch/events") return next();

        const chunks = [];
        let size = 0;
        req.on("data", chunk => {
          size += chunk.length;
          if (size > 16 * 1024) {
            res.statusCode = 413;
            res.setHeader("Content-Type", "application/json; charset=utf-8");
            res.end(JSON.stringify({ error: "payload_too_large", message: "Event payload exceeds 16384 bytes." }));
            req.destroy();
            return;
          }
          chunks.push(chunk);
        });
        req.on("end", () => {
          if (res.writableEnded) return;
          const body = Buffer.concat(chunks);
          const upstream = httpRequest({
            hostname: "127.0.0.1",
            port: 8081,
            path: `/api/v1/zerowatch/events${req.url.includes("?") ? req.url.slice(req.url.indexOf("?")) : ""}`,
            method: "POST",
            headers: {
              "Content-Type": req.headers["content-type"] || "application/json",
              "Content-Length": body.length,
              Accept: req.headers.accept || "application/json"
            }
          }, upstreamRes => {
            res.statusCode = upstreamRes.statusCode || 502;
            for (const header of ["content-type", "cache-control"]) {
              if (upstreamRes.headers[header]) res.setHeader(header, upstreamRes.headers[header]);
            }
            upstreamRes.pipe(res);
          });
          upstream.on("error", error => {
            if (!res.headersSent) {
              res.statusCode = 502;
              res.setHeader("Content-Type", "application/json; charset=utf-8");
            }
            res.end(JSON.stringify({ error: "zerowatch_proxy_error", message: error.message }));
          });
          upstream.end(body);
        });
        req.on("error", () => {
          if (!res.writableEnded) res.end();
        });
      });
    }
  };
}

export default defineConfig({
  plugins: [zeroWatchEventProxy(), react()],
  server: {
    host: "127.0.0.1",
    port: 5173,
    proxy: {
      "/api/scan": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true,
        rewrite: path => path.replace(/^\/api\/scan/, "/api/v1")
      },
      "/api/watch": {
        target: "http://127.0.0.1:8081",
        changeOrigin: true,
        rewrite: path => path.replace(/^\/api\/watch/, "/api/v1/zerowatch")
      },
      "/api/remediate": {
        target: "http://127.0.0.1:8083",
        changeOrigin: true,
        rewrite: path => path.replace(/^\/api\/remediate/, "/api/v1")
      }
    }
  }
});
