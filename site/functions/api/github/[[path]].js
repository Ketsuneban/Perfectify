const REPOSITORY = "Ketsuneban/Yeta";
const ALLOWED_PATH = /^(releases|contents(?:\/|$))/;

export async function onRequestGet(context) {
  const rawPath = Array.isArray(context.params.path)
    ? context.params.path.join("/")
    : String(context.params.path || "");

  if (!ALLOWED_PATH.test(rawPath)) {
    return Response.json({ error: "Unsupported GitHub endpoint" }, { status: 404 });
  }

  const incomingUrl = new URL(context.request.url);
  const githubUrl = new URL(`https://api.github.com/repos/${REPOSITORY}/${rawPath}`);
  githubUrl.search = incomingUrl.search;

  const cacheUrl = new URL(context.request.url);
  cacheUrl.pathname = `/__github-cache/${rawPath}`;
  const cacheKey = new Request(cacheUrl.toString(), { method: "GET" });
  const cache = caches.default;
  const cached = await cache.match(cacheKey);
  if (cached) {
    const response = new Response(cached.body, cached);
    response.headers.set("X-Yeta-GitHub-Source", "cloudflare-cache");
    return response;
  }

  const headers = new Headers({
    Accept: "application/vnd.github+json",
    "User-Agent": "Yeta-Website",
    "X-GitHub-Api-Version": "2022-11-28"
  });
  if (context.env.GITHUB_TOKEN) {
    headers.set("Authorization", `Bearer ${context.env.GITHUB_TOKEN}`);
  }

  const upstream = await fetch(githubUrl, { headers });
  const body = await upstream.arrayBuffer();
  const response = new Response(body, {
    status: upstream.status,
    headers: {
      "Content-Type": upstream.headers.get("Content-Type") || "application/json; charset=utf-8",
      "Cache-Control": upstream.ok
        ? "public, max-age=300, s-maxage=3600, stale-while-revalidate=86400"
        : "no-store",
      "X-Yeta-GitHub-Source": "github-api"
    }
  });

  if (upstream.ok) {
    context.waitUntil(cache.put(cacheKey, response.clone()));
  }
  return response;
}
