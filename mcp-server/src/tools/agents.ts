import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { CallToolRequestSchema } from "@modelcontextprotocol/sdk/types.js";
import { api, projectPath } from "../client.js";

export function registerAgentTools(server: Server): void {
  server.setRequestHandler(CallToolRequestSchema, async (request) => {
    const { name, arguments: args } = request.params;

    if (name === "kos_register_agent") {
      const resp = await api.post(projectPath("/agents"), {
        name: args?.name ?? "Claude Code",
        model: args?.model ?? "claude-sonnet-4-6",
        role: args?.role ?? "Implementer",
        agentType: "local",
        prompt: args?.prompt ?? null,
      });
      return {
        content: [{ type: "text", text: JSON.stringify(resp.data, null, 2) }],
      };
    }

    if (name === "kos_list_agents") {
      const resp = await api.get(projectPath("/agents"));
      return {
        content: [{ type: "text", text: JSON.stringify(resp.data, null, 2) }],
      };
    }

    throw new Error(`Unknown tool: ${name}`);
  });
}

export const agentToolDefinitions = [
  {
    name: "kos_register_agent",
    description:
      "Register this Claude Code session as a local agent in the KnowledgeOS project. " +
      "Call this at session start so you appear in the project dashboard. " +
      "Returns agent_id — save it for subsequent calls.",
    inputSchema: {
      type: "object",
      properties: {
        name: { type: "string", description: "Display name (default: 'Claude Code')" },
        model: { type: "string", description: "Model identifier (default: 'claude-sonnet-4-6')" },
        role: {
          type: "string",
          description: "Agent role (e.g. Implementer, Reviewer, Architect)",
        },
        prompt: { type: "string", description: "Optional system prompt for this agent" },
      },
      required: [],
    },
  },
  {
    name: "kos_list_agents",
    description: "List all agents currently registered in the project (pod and local).",
    inputSchema: { type: "object", properties: {}, required: [] },
  },
];
