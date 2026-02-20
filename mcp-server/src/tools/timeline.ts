import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { CallToolRequestSchema } from "@modelcontextprotocol/sdk/types.js";
import { api, projectPath } from "../client.js";

export function registerTimelineTools(server: Server): void {
  server.setRequestHandler(CallToolRequestSchema, async (request) => {
    const { name, arguments: args } = request.params;

    if (name === "kos_get_timeline") {
      const params: Record<string, string | number> = {};
      if (args?.cursor) params["cursor"] = args.cursor as string;
      if (args?.limit) params["limit"] = args.limit as number;
      if (args?.type) params["type"] = args.type as string;

      const resp = await api.get(projectPath("/timeline"), { params });
      return {
        content: [{ type: "text", text: JSON.stringify(resp.data, null, 2) }],
      };
    }

    throw new Error(`Unknown tool: ${name}`);
  });
}

export const timelineToolDefinitions = [
  {
    name: "kos_get_timeline",
    description:
      "Fetch recent timeline events for the project. Events include agent activity, " +
      "changeset submissions, lock acquisitions, and memory writes.",
    inputSchema: {
      type: "object",
      properties: {
        cursor: { type: "string", description: "Pagination cursor from a previous call" },
        limit: { type: "number", description: "Number of events per page (default: 20, max: 100)" },
        type: {
          type: "string",
          description:
            "Filter by event type (e.g. changeset_submitted, lock_acquired, memory_written)",
        },
      },
      required: [],
    },
  },
];
