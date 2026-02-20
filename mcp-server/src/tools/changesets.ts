import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { CallToolRequestSchema } from "@modelcontextprotocol/sdk/types.js";
import { api, projectPath } from "../client.js";

export function registerChangesetTools(server: Server): void {
  server.setRequestHandler(CallToolRequestSchema, async (request) => {
    const { name, arguments: args } = request.params;

    if (name === "kos_submit_changeset") {
      const resp = await api.post(projectPath("/changesets"), {
        intent: args?.intent,
        filesChanged: args?.filesChanged ?? [],
        diff: args?.diff ?? "",
        agentId: args?.agentId ?? null,
        autoApplyPolicy: args?.autoApplyPolicy ?? "never",
        testsRun: args?.testsRun ?? null,
      });
      return {
        content: [{ type: "text", text: JSON.stringify(resp.data, null, 2) }],
      };
    }

    throw new Error(`Unknown tool: ${name}`);
  });
}

export const changesetToolDefinitions = [
  {
    name: "kos_submit_changeset",
    description:
      "Submit a changeset (diff) for human review. Use this instead of git commit to enable " +
      "review, approval, and rollback. Acquire locks on the affected files first with kos_acquire_lock.",
    inputSchema: {
      type: "object",
      properties: {
        intent: {
          type: "string",
          description: "Human-readable description of what this change does and why",
        },
        filesChanged: {
          type: "array",
          items: { type: "string" },
          description: "List of relative file paths modified",
        },
        diff: {
          type: "string",
          description: "Unified diff of the changes",
        },
        agentId: {
          type: "string",
          description: "Agent UUID that is submitting the changeset",
        },
        autoApplyPolicy: {
          type: "string",
          enum: ["always", "never", "on_tests_pass"],
          description: "When to auto-apply without human approval (default: never)",
        },
        testsRun: {
          type: "array",
          items: { type: "string" },
          description: "List of test names run before submitting (optional)",
        },
      },
      required: ["intent", "filesChanged", "diff"],
    },
  },
];
