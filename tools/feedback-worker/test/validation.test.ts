import { describe, expect, it, vi } from "vitest";
import worker from "../src/index";
import { GOOD, IP, githubCalls, makeEnv, post, stubFetch, created, useFixedEnvironment } from "./helpers";

useFixedEnvironment();

async function send(body: unknown, opts?: Parameters<typeof post>[1]) {
  const env = makeEnv();
  const gh = stubFetch(() => created());
  const res = await worker.fetch(post(body, opts), env);
  return { res, gh, json: await res.json() as Record<string, unknown> };
}

// Builds a valid body of exactly `bytes` UTF-8 bytes by padding with a "pad" field.
function paddedBody(bytes: number): string {
  const size = (s: string) => new TextEncoder().encode(s).length;
  const base = size(JSON.stringify({ ...GOOD, pad: "" }));
  const raw = JSON.stringify({ ...GOOD, pad: "x".repeat(bytes - base) });
  expect(size(raw)).toBe(bytes);
  return raw;
}

describe("request validation", () => {
  it("accepts the valid body and writes one note", async () => {
    const { res, gh, json } = await send(GOOD);
    expect(res.status).toBe(200);
    expect(json).toEqual({ ok: true });
    expect(githubCalls(gh)).toHaveLength(1);
  });

  it.each([
    ["9 chars", "a".repeat(9), 400],
    ["10 chars", "a".repeat(10), 200],
    ["2000 chars", "a".repeat(2000), 200],
    ["2001 chars", "a".repeat(2001), 400],
  ])("text length %s -> expected %s", async (_label, text, status) => {
    const { res } = await send({ ...GOOD, text });
    expect(res.status).toBe(status);
  });

  it("trims before counting: 9 real chars with spaces around -> 400", async () => {
    const { res } = await send({ ...GOOD, text: "   aaaaaaaaa   " });
    expect(res.status).toBe(400);
  });

  it("rejects a non-string text", async () => {
    const { res } = await send({ ...GOOD, text: 12345678901 });
    expect(res.status).toBe(400);
  });

  it.each(["bug", "idea", "other"])("accepts category %s", async (category) => {
    const { res } = await send({ ...GOOD, category });
    expect(res.status).toBe(200);
  });

  it.each(["praise", "Bug", "", "bug "])("rejects category %j", async (category) => {
    const { res } = await send({ ...GOOD, category });
    expect(res.status).toBe(400);
  });

  it.each(["modVersion", "loader", "mcVersion"] as const)("%s: 32 chars ok, 33 chars -> 400", async (field) => {
    const ok = await send({ ...GOOD, [field]: "a".repeat(32) });
    expect(ok.res.status).toBe(200);
    const bad = await send({ ...GOOD, [field]: "a".repeat(33) });
    expect(bad.res.status).toBe(400);
  });

  it.each(["modVersion", "loader", "mcVersion"] as const)("%s: rejects empty and invalid characters", async (field) => {
    for (const value of ["", "1.5 0", "1.5/0", "1.5<b>", "1,5", "ção"]) {
      const { res } = await send({ ...GOOD, [field]: value });
      expect(res.status, `${field}=${value}`).toBe(400);
    }
  });

  it("accepts the allowed punctuation . _ + -", async () => {
    const { res } = await send({ ...GOOD, modVersion: "1.5.0-beta+build_2" });
    expect(res.status).toBe(200);
  });

  it("rejects a missing field", async () => {
    const { mcVersion: _omit, ...partial } = GOOD;
    const { res } = await send(partial);
    expect(res.status).toBe(400);
  });

  it("ignores extra fields and never writes them", async () => {
    const env = makeEnv();
    const gh = stubFetch(() => created());
    const res = await worker.fetch(
      post({ ...GOOD, playerName: "NomeDoJogadorXyz", ip: "198.51.100.44" }),
      env,
    );
    expect(res.status).toBe(200);
    const body = String(githubCalls(gh)[0][1]?.body);
    expect(body).not.toContain("NomeDoJogadorXyz");
    expect(body).not.toContain("198.51.100.44");
  });

  it("rejects invalid JSON with 400 and an error body", async () => {
    const { res, json } = await send(undefined, { raw: "{not json" });
    expect(res.status).toBe(400);
    expect(typeof json.error).toBe("string");
  });

  it.each([
    ["an array", "[]"],
    ["null", "null"],
    ["a string", '"hello world"'],
    ["an empty body", ""],
  ])("rejects a body that is %s", async (_label, raw) => {
    const { res } = await send(undefined, { raw });
    expect(res.status).toBe(400);
  });

  it("rejects a wrong content type with 400", async () => {
    const { res } = await send(GOOD, { headers: { "content-type": "text/plain" } });
    expect(res.status).toBe(400);
  });

  it("accepts content type with a charset parameter", async () => {
    const { res } = await send(GOOD, { headers: { "content-type": "application/json; charset=utf-8" } });
    expect(res.status).toBe(200);
  });

  it.each(["GET", "PUT", "DELETE", "OPTIONS"])("method %s -> 405", async (method) => {
    const { res, json } = await send(GOOD, { method });
    expect(res.status).toBe(405);
    expect(typeof json.error).toBe("string");
  });

  it("accepts POST on any path", async () => {
    const env = makeEnv();
    stubFetch(() => created());
    const res = await worker.fetch(new Request("https://feedback.example.test/anything/else", {
      method: "POST",
      headers: { "content-type": "application/json", "cf-connecting-ip": IP },
      body: JSON.stringify(GOOD),
    }), env);
    expect(res.status).toBe(200);
  });

  it("body of exactly 8192 bytes is accepted when valid", async () => {
    const raw = paddedBody(8192);
    const { res } = await send(undefined, { raw });
    expect(res.status).toBe(200);
  });

  it("body of 8193 bytes -> 413 without calling GitHub", async () => {
    const raw = paddedBody(8193);
    const { res, gh } = await send(undefined, { raw });
    expect(res.status).toBe(413);
    expect(githubCalls(gh)).toHaveLength(0);
  });

  it("a declared Content-Length above 8 KB -> 413 even with a small body", async () => {
    const { res, gh } = await send(undefined, { raw: JSON.stringify(GOOD), headers: { "content-length": "9000" } });
    expect(res.status).toBe(413);
    expect(githubCalls(gh)).toHaveLength(0);
  });

  it("does not echo the player text or the IP in error responses", async () => {
    const res = await worker.fetch(post({ ...GOOD, text: "SEGREDO-DO-JOGADOR", category: "nope" }), makeEnv());
    expect(res.status).toBe(400);
    const body = await res.text();
    expect(body).not.toContain("SEGREDO-DO-JOGADOR");
    expect(body).not.toContain(IP);
  });

  it("does not log text or IP on rejection", async () => {
    const logs = [vi.spyOn(console, "log"), vi.spyOn(console, "warn"), vi.spyOn(console, "error"), vi.spyOn(console, "info")];
    await send({ ...GOOD, text: "SEGREDO-DO-JOGADOR", category: "nope" });
    for (const spy of logs) {
      for (const call of spy.mock.calls) {
        expect(JSON.stringify(call)).not.toContain("SEGREDO-DO-JOGADOR");
        expect(JSON.stringify(call)).not.toContain(IP);
      }
    }
  });
});
