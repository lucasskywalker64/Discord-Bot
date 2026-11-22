export type Session = {
  claimAvailable: boolean;
  authenticated: boolean;
  username: string | null;
  csrfToken: string;
};

export type Status = {
  state: "DISABLED" | "CONFIGURED" | "CONNECTING" | "CONNECTED" | "UNAVAILABLE";
  message: string;
  lastFailure: string | null;
};

export type Configuration = {
  providerChecks?: Record<string, "SUCCEEDED" | "FAILED" | "UNAVAILABLE">;
  setupStep: "PROVIDERS" | "DEPLOYMENT" | "STORAGE" | "COMPLETE";
  publicUrl: string | null;
  discord: {
    botEnabled: boolean;
    botTokenConfigured: boolean;
    oauthEnabled: boolean;
    clientId: string | null;
    clientSecretConfigured: boolean;
    callbackUrl: string | null;
    suggestedCallbackUrl: string;
    status: Status;
  };
  twitch: {
    enabled: boolean;
    clientId: string | null;
    clientSecretConfigured: boolean;
    callbackUrl: string | null;
    status: Status;
  };
  storage: {
    enabled: boolean;
    endpoint: string | null;
    bucket: string | null;
    region: string | null;
    accessKeyConfigured: boolean;
    secretKeyConfigured: boolean;
    status: Status;
    verifiedAt: string | null;
  };
};

export class ApiError extends Error {
  constructor(public status: number, public fields: Record<string, string>) {
    super("The request could not be completed.");
  }
}

export async function adminRequest<T>(path: string, body?: BodyInit, csrf?: string): Promise<T> {
  const response = await fetch(`/admin/api/${path}`, {
    method: csrf ? "POST" : "GET",
    body,
    headers: csrf ? {
      "X-CSRF-TOKEN": csrf,
      ...(typeof body === "string" ? { "Content-Type": "application/json" } : {}),
    } : {},
  });
  if (!response.ok) {
    const result = await response.json().catch(() => undefined);
    throw new ApiError(response.status, result?.errors ?? {});
  }
  return response.status === 204 ? undefined as T : response.json();
}
