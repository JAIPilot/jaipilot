import { dirname, join } from "node:path";

const SITE = "https://www.jaipilot.com";
type Session = { access_token: string; refresh_token: string; expires_at: number; email: string };

function sessionPath(): string {
  const base = Deno.env.get("JAIPILOT_CONFIG_DIR") ||
    (Deno.build.os === "windows" ? Deno.env.get("APPDATA") : Deno.env.get("XDG_CONFIG_HOME")) ||
    join(Deno.env.get("HOME") || Deno.env.get("USERPROFILE") || ".", ".config");
  return join(base, "jaipilot", "session.json");
}

async function readSession(): Promise<Session | null> {
  try {
    const value = JSON.parse(await Deno.readTextFile(sessionPath()));
    return typeof value?.access_token === "string" &&
        typeof value?.refresh_token === "string" &&
        typeof value?.expires_at === "number" &&
        typeof value?.email === "string"
      ? value
      : null;
  } catch (error) {
    if (error instanceof Deno.errors.NotFound) return null;
    throw new Error("JAIPilot session could not be read");
  }
}

async function saveSession(session: Session): Promise<void> {
  const path = sessionPath();
  const dir = dirname(path);
  await Deno.mkdir(dir, { recursive: true, mode: 0o700 });
  const temporary = `${path}.${crypto.randomUUID()}.tmp`;
  try {
    await Deno.writeTextFile(temporary, JSON.stringify(session), { mode: 0o600 });
    await Deno.chmod(temporary, 0o600).catch(() => {});
    await Deno.rename(temporary, path);
  } finally {
    await Deno.remove(temporary).catch(() => {});
  }
}

export async function logout(): Promise<void> {
  await Deno.remove(sessionPath()).catch((error) => {
    if (!(error instanceof Deno.errors.NotFound)) throw error;
  });
}

async function openBrowser(url: string): Promise<boolean> {
  const command = Deno.build.os === "darwin"
    ? ["open", url]
    : Deno.build.os === "windows"
    ? ["cmd", "/c", "start", "", url]
    : ["xdg-open", url];
  try {
    return (await new Deno.Command(command[0], {
      args: command.slice(1),
      stdout: "null",
      stderr: "null",
    }).output()).success;
  } catch {
    return false;
  }
}

export async function login(): Promise<string> {
  const state = crypto.randomUUID();
  let done!: (session: Session) => void;
  const completed = new Promise<Session>((resolve) => done = resolve);
  const controller = new AbortController();
  const server = Deno.serve({
    hostname: "127.0.0.1",
    port: 0,
    signal: controller.signal,
    onListen: () => {},
  }, (request) => {
    const url = new URL(request.url);
    if (
      request.method !== "GET" || url.pathname !== "/auth/callback" ||
      url.searchParams.get("state") !== state
    ) {
      return new Response("Invalid callback", { status: 400 });
    }
    const access_token = url.searchParams.get("access_token") ?? "";
    const refresh_token = url.searchParams.get("refresh_token") ?? "";
    const expires_at = Number(url.searchParams.get("expires_at"));
    const email = url.searchParams.get("email") ?? "";
    if (!access_token || !refresh_token || !email || !Number.isFinite(expires_at)) {
      return new Response("Invalid session", { status: 400 });
    }
    done({ access_token, refresh_token, expires_at, email });
    return new Response("JAIPilot sign-in complete. You can close this tab.", {
      headers: {
        "content-type": "text/plain",
        "cache-control": "no-store",
        "referrer-policy": "no-referrer",
      },
    });
  });
  try {
    const port = (server.addr as Deno.NetAddr).port;
    const url = new URL(`${SITE}/plugin-login`);
    url.searchParams.set("redirect_uri", `http://127.0.0.1:${port}/auth/callback`);
    url.searchParams.set("state", state);
    console.error(`Complete JAIPilot sign-in: ${url}`);
    if (!await openBrowser(url.toString())) {
      console.error("Open the sign-in URL above in a browser on this computer.");
    }
    let timer: ReturnType<typeof setTimeout> | undefined;
    const timeout = new Promise<never>((_, reject) => {
      timer = setTimeout(() => reject(new Error("Sign-in timed out")), 180_000);
    });
    const session = await Promise.race([completed, timeout]).finally(() => clearTimeout(timer));
    await saveSession(session);
    return session.email;
  } finally {
    controller.abort();
    await server.finished.catch(() => {});
  }
}

export async function status(): Promise<string | null> {
  return (await readSession())?.email ?? null;
}

export async function bearerToken(): Promise<string> {
  const session = await readSession();
  if (!session) throw new Error("Sign in first with `jaipilot auth login`");
  if (session.expires_at > Math.floor(Date.now() / 1000) + 60) return session.access_token;
  let response: Response;
  try {
    response = await fetch(`${SITE}/plugin-refresh`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ refresh_token: session.refresh_token }),
      signal: AbortSignal.timeout(20_000),
    });
  } catch {
    throw new Error("Could not refresh JAIPilot sign-in; check your connection");
  }
  if (response.status === 401 || response.status === 403) {
    await logout();
    throw new Error("JAIPilot sign-in expired; run `jaipilot auth login`");
  }
  if (!response.ok) throw new Error(`Could not refresh JAIPilot sign-in (HTTP ${response.status})`);
  const value = await response.json();
  if (typeof value.access_token !== "string" || !value.access_token) {
    throw new Error("JAIPilot returned an invalid session");
  }
  await saveSession({
    access_token: value.access_token,
    refresh_token: value.refresh_token || session.refresh_token,
    expires_at: Number(value.expires_at) || 0,
    email: value.email || session.email,
  });
  return value.access_token;
}
