import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { CallToolRequestSchema } from "@modelcontextprotocol/sdk/types.js";
import { api, projectPath } from "../client.js";

export function registerMemoryTools(server: Server): void {
  server.setRequestHandler(CallToolRequestSchema, async (request) => {
    const { name, arguments: args } = request.params;

    if (name === "kos_write_memory") {
      const resp = await api.post(projectPath("/memory"), {
        title: args?.title,
        content: args?.content,
        justification: args?.justification,
        layer: args?.layer ?? "scratch",
        scopeKey: args?.scopeKey ?? null,
        tags: args?.tags ?? null,
      });
      return {
        content: [{ type: "text", text: JSON.stringify(resp.data, null, 2) }],
      };
    }

    if (name === "kos_search_memory") {
      const resp = await api.post(projectPath("/memory/search"), {
        query: args?.query,
        layer: args?.layer ?? null,
        limit: args?.limit ?? 5,
      });
      return {
        content: [{ type: "text", text: JSON.stringify(resp.data, null, 2) }],
      };
    }

    throw new Error(`Unknown tool: ${name}`);
  });
}

export const memoryToolDefinitions = [
  {
    name: "kos_write_memory",
    description:
      "Save a memory entry to the KnowledgeOS knowledge store. " +
      "Use layer='canonical' for important decisions that should persist. " +
      "Use layer='scratch' for temporary findings (auto-expires in 4 hours).",
    inputSchema: {
      type: "object",
      properties: {
        title: { type: "string", description: "Short title for the memory entry" },
        content: { type: "string", description: "Full content to store" },
        justification: {
          type: "string",
          description: "Why this is being stored (required)",
        },
        layer: {
          type: "string",
          enum: ["canonical", "feature", "scratch"],
          description: "Memory layer (default: scratch)",
        },
        scopeKey: { type: "string", description: "Optional grouping key (e.g. feature name)" },
        tags: {
          type: "array",
          items: { type: "string" },
          description: "Optional tags for retrieval",
        },
      },
      required: ["title", "content", "justification"],
    },
  },
  {
    name: "kos_search_memory",
    description: "Semantic search over project memory entries.",
    inputSchema: {
      type: "object",
      properties: {
        query: { type: "string", description: "Search query (natural language)" },
        layer: {
          type: "string",
          enum: ["canonical", "feature", "scratch"],
          description: "Limit to a specific layer (optional)",
        },
        limit: { type: "number", description: "Max results (default: 5)" },
      },
      required: ["query"],
    },
  },
];
