import axios, { AxiosInstance } from "axios";

const KOS_API = process.env.KOS_API ?? "http://localhost:8080";
const KOS_API_KEY = process.env.KOS_API_KEY ?? "dev-local-key";
const KOS_PROJECT_ID = process.env.KOS_PROJECT_ID ?? "";

export const projectId = KOS_PROJECT_ID;

export const api: AxiosInstance = axios.create({
  baseURL: KOS_API,
  headers: {
    "Content-Type": "application/json",
    "X-KOS-API-Key": KOS_API_KEY,
  },
  timeout: 10_000,
});

/** Convenience: base path for the configured project. */
export function projectPath(sub = ""): string {
  if (!KOS_PROJECT_ID) throw new Error("KOS_PROJECT_ID is not set");
  return `/api/v1/projects/${KOS_PROJECT_ID}${sub}`;
}
