import { bindings, defineConfig } from "cf/config";

import * as entrypoint from "./src/index.ts" with { type: "cf-worker" };

export default defineConfig({
  worker: {
    name: "the-dream-table-feedback",
    entrypoint,
    compatibilityDate: "2026-09-17",
    env: {
      // Replace with the id printed by `cf kv namespaces create` (see README).
      FEEDBACK_KV: bindings.kv({ id: "REPLACE_WITH_KV_NAMESPACE_ID" }),
      // Namespace ids are account-wide integers as strings; 3 requests per IP per 60 seconds.
      FEEDBACK_RATE_LIMITER: bindings.rateLimit({
        namespace: "1001",
        simple: { limit: 3, period: 60 },
      }),
      GITHUB_TOKEN: bindings.secret(),
      GITHUB_REPO: bindings.text("davisrr-18/rede-neural"),
      GITHUB_BRANCH: bindings.text("main"),
    },
  },
});
