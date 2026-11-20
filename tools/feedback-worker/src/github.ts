// Creates one file through the GitHub contents API. Returns the HTTP status only;
// the response body is never read, so nothing from GitHub can leak into our reply.

export interface GitHubTarget {
  token: string;
  repo: string;
  branch: string;
}

export async function createFile(
  target: GitHubTarget,
  path: string,
  message: string,
  content: string,
): Promise<number> {
  const bytes = new TextEncoder().encode(content);
  const res = await fetch(`https://api.github.com/repos/${target.repo}/contents/${path}`, {
    method: "PUT",
    headers: {
      Authorization: `Bearer ${target.token}`,
      "User-Agent": "the-dream-table-feedback",
      Accept: "application/vnd.github+json",
      "X-GitHub-Api-Version": "2026-03-10",
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      message,
      content: btoa(String.fromCharCode(...bytes)),
      branch: target.branch,
    }),
  });
  return res.status;
}
