import { createFile } from "./github";
import { buildNote, newId, notePath, parseFeedback, type Feedback } from "./note";

export interface Env {
  FEEDBACK_RATE_LIMITER: RateLimit;
  FEEDBACK_KV: KVNamespace;
  GITHUB_TOKEN: string;
  GITHUB_REPO: string;
  GITHUB_BRANCH: string;
}

const MAX_BODY_BYTES = 8192;
const DAILY_CAP = 200;

// shortcut: the daily cap is a non-atomic read-then-write in KV, so simultaneous
// submissions can pass it by a few. Upgrade to a Durable Object counter if it must be exact.
const CAP_TTL_SECONDS = 172800; // KV requires at least 60; two days keeps yesterday's key until it is stale

function json(body: unknown, status: number, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8", ...headers },
  });
}

// Reads at most `max` bytes; returns null as soon as the body is larger.
async function readLimited(body: ReadableStream<Uint8Array> | null, max: number): Promise<Uint8Array | null> {
  if (body === null) return new Uint8Array(0);
  const reader = body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) break;
    total += value.byteLength;
    if (total > max) {
      await reader.cancel();
      return null;
    }
    chunks.push(value);
  }
  const out = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    out.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return out;
}

// One GitHub write. A 422 means the file already exists; the caller retries with another id.
async function commit(env: Env, fields: Feedback, date: string, id: string): Promise<number> {
  return createFile(
    { token: env.GITHUB_TOKEN, repo: env.GITHUB_REPO, branch: env.GITHUB_BRANCH },
    notePath(date, fields.category, id),
    `feedback: ${fields.category} ${id}`,
    buildNote(fields, date),
  );
}

export default {
  async fetch(request, env): Promise<Response> {
    if (request.method !== "POST") {
      return json({ error: "method not allowed" }, 405, { allow: "POST" });
    }

    const ip = request.headers.get("CF-Connecting-IP") ?? "unknown";
    const ipLimit = await env.FEEDBACK_RATE_LIMITER.limit({ key: ip });
    if (!ipLimit.success) return json({ error: "rate limit exceeded" }, 429);

    const type = request.headers.get("content-type")?.split(";")[0].trim().toLowerCase();
    if (type !== "application/json") return json({ error: "content type must be application/json" }, 400);

    const declared = Number(request.headers.get("content-length") ?? 0);
    if (declared > MAX_BODY_BYTES) return json({ error: "body too large" }, 413);
    const bytes = await readLimited(request.body, MAX_BODY_BYTES);
    if (bytes === null) return json({ error: "body too large" }, 413);

    let body: unknown;
    try {
      body = JSON.parse(new TextDecoder("utf-8", { fatal: true, ignoreBOM: false }).decode(bytes));
    } catch {
      return json({ error: "invalid JSON" }, 400);
    }
    const fields = parseFeedback(body);
    if (fields === null) return json({ error: "invalid feedback fields" }, 400);

    const date = new Date().toISOString().slice(0, 10);
    const capKey = `feedback-count:${date}`;
    const count = Number((await env.FEEDBACK_KV.get(capKey)) ?? 0);
    if (count >= DAILY_CAP) return json({ error: "daily limit reached" }, 429);

    // 0 means no HTTP response (network error). Only the status is logged, never the body or text.
    let status = 0;
    try {
      status = await commit(env, fields, date, newId());
      if (status === 422) status = await commit(env, fields, date, newId());
    } catch {
      status = 0;
    }
    if (status < 200 || status >= 300) {
      console.error(`github-write-failed status=${status}`);
      return json({ error: "could not save feedback" }, 502);
    }

    // Counted only after GitHub accepted the note, so failed writes do not use up the day's cap.
    // The note is already saved, so a counter write error must not turn this into a failure.
    await env.FEEDBACK_KV.put(capKey, String(count + 1), { expirationTtl: CAP_TTL_SECONDS }).catch(() =>
      console.error("daily-counter-write-failed"),
    );
    return json({ ok: true }, 200);
  },
} satisfies ExportedHandler<Env>;
