import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { CallToolRequestSchema } from "@modelcontextprotocol/sdk/types.js";
import { api, projectPath } from "../client.js";

export function registerLockTools(server: Server): void {
  server.setRequestHandler(CallToolRequestSchema, async (request) => {
    const { name, arguments: args } = request.params;

    if (name === "kos_acquire_lock") {
      const resp = await api.post(projectPath("/locks"), {
        filePath: args?.filePath,
        lockType: args?.lockType ?? "write",
        durationSeconds: args?.durationSeconds ?? 300,
        agentId: args?.agentId ?? null,
      });
      return {
        content: [{ type: "text", text: JSON.stringify(resp.data, null, 2) }],
      };
    }

    if (name === "kos_release_lock") {
      await api.delete(projectPath(`/locks/${args?.lockId}`));
      return {
        content: [{ type: "text", text: `Lock ${args?.lockId} released.` }],
      };
    }

    throw new Error(`Unknown tool: ${name}`);
  });
}

export const lockToolDefinitions = [
  {
    name: "kos_acquire_lock",
    description:
      "Acquire a file lock before editing. Use a write lock to prevent concurrent edits. " +
      "Returns a lock ID — pass it to kos_release_lock when done.",
    inputSchema: {
      type: "object",
      properties: {
        filePath: {
          type: "string",
          description: "Relative file path to lock (e.g. 'src/main/java/Foo.java')",
        },
        lockType: {
          type: "string",
          enum: ["read", "write"],
          description: "Lock type (default: write)",
        },
        durationSeconds: {
          type: "number",
          description: "Lock duration in seconds (default: 300)",
        },
        agentId: { type: "string", description: "Agent UUID (optional)" },
      },
      required: ["filePath"],
    },
  },
  {
    name: "kos_release_lock",
    description: "Release a previously acquired file lock.",
    inputSchema: {
      type: "object",
      properties: {
        lockId: { type: "string", description: "Lock UUID returned by kos_acquire_lock" },
      },
      required: ["lockId"],
    },
  },
];
