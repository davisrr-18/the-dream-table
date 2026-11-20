import { describe, expect, it } from "vitest";
import worker from "../src/index";
import { GOOD, decodeContent, githubCalls, makeEnv, post, stubFetch, created, useFixedEnvironment } from "./helpers";

useFixedEnvironment();

// Sends `text` as the feedback and returns the quoted lines of the written note.
async function quotedLines(text: string): Promise<string[]> {
  const gh = stubFetch(() => created());
  const res = await worker.fetch(post({ ...GOOD, text }), makeEnv());
  expect(res.status).toBe(200);
  const body = JSON.parse(String(githubCalls(gh)[0][1]?.body)) as { content: string };
  const note = decodeContent(body.content);
  const start = note.indexOf("## Feedback\n\n");
  const end = note.indexOf("\n\n## Dados técnicos");
  return note.slice(start + "## Feedback\n\n".length, end).split("\n");
}

describe("text sanitization before the note is written", () => {
  it("neutralizes wiki links, embeds, markdown links, tags, frontmatter, fences, comments and callouts", async () => {
    const lines = await quotedLines(
      [
        "Veja [[nota]] e ![[imagem]]",
        "tag #projeto e #Tag2",
        "---",
        "```",
        "%%oculto%%",
        "> [!warning] falso",
      ].join("\n"),
    );
    expect(lines).toEqual([
      "> Veja \\[\\[nota\\]\\] e !\\[\\[imagem\\]\\]",
      "> tag \\#projeto e \\#Tag2",
      "> \\---",
      "> \\```",
      "> \\%\\%oculto\\%\\%",
      "> \\> \\[!warning\\] falso",
    ]);
  });

  it("normalizes CRLF, removes control characters and collapses 3+ line breaks", async () => {
    const lines = await quotedLines("linha um\r\n\r\n\r\n\r\nlinha\u0007 dois\u0000 aqui");
    expect(lines).toEqual(["> linha um", "> ", "> linha dois aqui"]);
  });

  it("keeps every line of the text inside the quote, including blank lines", async () => {
    const lines = await quotedLines("primeira\n\n# titulo falso\n\n- item\n\nfim do texto");
    expect(lines.every((l) => l.startsWith("> "))).toBe(true);
    expect(lines).toHaveLength(7);
  });

  it("does not change the text when there is nothing to escape", async () => {
    const lines = await quotedLines("Tudo bem por aqui, sem nada especial.");
    expect(lines).toEqual(["> Tudo bem por aqui, sem nada especial."]);
  });
});
