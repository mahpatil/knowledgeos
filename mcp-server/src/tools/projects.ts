import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { CallToolRequestSchema, ListToolsRequestSchema } from "@modelcontextprotocol/sdk/types.js";
import { api } from "../client.js";

export function registerProjectTools(server: Server): void {
  server.setRequestHandler(ListToolsRequestSchema, async () => ({
    tools: [
      {
        name: "kos_list_projects",
        description: "List all KnowledgeOS projects",
        inputSchema: { type: "object", properties: {}, required: [] },
      },
      {
        name: "kos_create_project",
        description:
          "Create a new KnowledgeOS project. Returns project_id which should be set as KOS_PROJECT_ID in .mcp.json.",
        inputSchema: {
          type: "object",
          properties: {
            name: { type: "string", description: "Project name" },
            type: {
              type: "string",
              enum: ["software", "content", "migration", "research"],
              description: "Project type",
            },
          },
          required: ["name", "type"],
        },
      },
    ],
  }));

  server.setRequestHandler(CallToolRequestSchema, async (request, extra) => {
    const { name, arguments: args } = request.params;

    if (name === "kos_list_projects") {
      const resp = await api.get("/api/v1/projects");
      return {
        content: [{ type: "text", text: JSON.stringify(resp.data, null, 2) }],
      };
    }

    if (name === "kos_create_project") {
      const resp = await api.post("/api/v1/projects", {
        name: args?.name,
        type: args?.type,
      });
      return {
        content: [{ type: "text", text: JSON.stringify(resp.data, null, 2) }],
      };
    }

    throw new Error(`Unknown tool: ${name}`);
  });
}
