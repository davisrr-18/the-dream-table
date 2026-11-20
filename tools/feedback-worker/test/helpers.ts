import { afterEach, beforeEach, vi } from "vitest";
import type worker from "../src/index";
import type { Env } from "../src/index";

export type Incoming = Parameters<typeof worker.fetch>[0];

export const IP = "203.0.113.7";
export const TEXT = "O botão do formulário não responde.";

export const GOOD = {
  text: TEXT,
  category: "bug",
  modVersion: "1.5.0",
  loader: "fabric",
  mcVersion: "1.21.1",
};

export class FakeKV {
  store = new Map<string, string>();
  async get(key: string): Promise<string | null> {
    return this.store.get(key) ?? null;
  }
  async put(key: string, value: string): Promise<void> {
    this.store.set(key, value);
  }
}

export function makeEnv(overrides: Partial<Env> = {}): Env & { kv: FakeKV } {
  const kv = new FakeKV();
  return {
    FEEDBACK_RATE_LIMITER: { limit: async () => ({ success: true }) } as unknown as RateLimit,
    FEEDBACK_KV: kv as unknown as KVNamespace,
    GITHUB_TOKEN: "fake-token-for-tests",
    GITHUB_REPO: "owner/repo",
    GITHUB_BRANCH: "main",
    kv,
    ...overrides,
  };
}

export function post(
  body: unknown,
  opts: { raw?: string; headers?: Record<string, string>; method?: string } = {},
): Incoming {
  const raw = opts.raw ?? JSON.stringify(body);
  return new Request("https://feedback.example.test/", {
    method: opts.method ?? "POST",
    headers: {
      "content-type": "application/json",
      "cf-connecting-ip": IP,
      ...opts.headers,
    },
    body: opts.method === "GET" || opts.method === "OPTIONS" ? undefined : raw,
  }) as Incoming;
}

// GitHub stub: every call to api.github.com goes through this responder.
export function stubFetch(responder: (url: string, init: RequestInit) => Response | Promise<Response>) {
  const mock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) =>
    responder(String(input), init ?? {}),
  );
  vi.stubGlobal("fetch", mock);
  return mock;
}

export function githubCalls(mock: ReturnType<typeof stubFetch>) {
  return mock.mock.calls.filter((c) => String(c[0]).startsWith("https://api.github.com/"));
}

export function created(): Response {
  return new Response("{}", { status: 201 });
}

// Decodes the base64 content of a PUT body as UTF-8.
export function decodeContent(b64: string): string {
  const bin = atob(b64);
  const bytes = Uint8Array.from(bin, (ch) => ch.charCodeAt(0));
  return new TextDecoder("utf-8", { fatal: true, ignoreBOM: false }).decode(bytes);
}

// Fixed clock (2026-11-20 UTC) and fixed random bytes, so paths and notes are exact.
// Call once at the top of a test file.
let idQueue: number[][] = [];
export function queueIds(...ids: number[][]) {
  idQueue = ids;
}

export function useFixedEnvironment() {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-11-20T12:00:00Z"));
    idQueue = [];
    vi.spyOn(crypto, "getRandomValues").mockImplementation(<T extends ArrayBufferView>(arr: T): T => {
      const next = idQueue.shift() ?? [0xa1, 0xb2, 0xc3];
      new Uint8Array(arr.buffer, arr.byteOffset, arr.byteLength).set(next);
      return arr;
    });
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });
}
