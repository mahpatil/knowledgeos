import { Server } from "@modelcontextprotocol/sdk/server/index.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import {
  CallToolRequestSchema,
  ListToolsRequestSchema,
} from "@modelcontextprotocol/sdk/types.js";
import { api, projectPath } from "./client.js";
import { agentToolDefinitions } from "./tools/agents.js";
import { changesetToolDefinitions } from "./tools/changesets.js";
import { lockToolDefinitions } from "./tools/locks.js";
import { memoryToolDefinitions } from "./tools/memory.js";
import { timelineToolDefinitions } from "./tools/timeline.js";

const server = new Server(
  { name: "knowledgeos", version: "1.0.0" },
  { capabilities: { tools: {} } }
);

// ── Tool registry ──────────────────────────────────────────────────────────

server.setRequestHandler(ListToolsRequestSchema, async () => ({
  tools: [
    {
      name: "kos_list_projects",
      description: "List all KnowledgeOS projects",
      inputSchema: { type: "object" as const, properties: {}, required: [] },
    },
    {
      name: "kos_create_project",
      description:
        "Create a new KnowledgeOS project. Returns project_id — set it as KOS_PROJECT_ID in .mcp.json.",
      inputSchema: {
        type: "object" as const,
        properties: {
          name: { type: "string", description: "Project name" },
          type: {
            type: "string",
            enum: ["software", "content", "migration", "research"],
          },
        },
        required: ["name", "type"],
      },
    },
    ...agentToolDefinitions,
    ...changesetToolDefinitions,
    ...lockToolDefinitions,
    ...memoryToolDefinitions,
    ...timelineToolDefinitions,
  ],
}));

// ── Dispatch ───────────────────────────────────────────────────────────────

server.setRequestHandler(CallToolRequestSchema, async (request) => {
  const { name, arguments: args } = request.params;

  try {
    switch (name) {
      // Projects
      case "kos_list_projects": {
        const resp = await api.get("/api/v1/projects");
        return text(resp.data);
      }
      case "kos_create_project": {
        const resp = await api.post("/api/v1/projects", {
          name: args?.name,
          type: args?.type,
        });
        return text(resp.data);
      }

      // Agents
      case "kos_register_agent": {
        const resp = await api.post(projectPath("/agents"), {
          name: args?.name ?? "Claude Code",
          model: args?.model ?? "claude-sonnet-4-6",
          role: args?.role ?? "Implementer",
          agentType: "local",
          prompt: args?.prompt ?? null,
        });
        return text(resp.data);
      }
      case "kos_list_agents": {
        const resp = await api.get(projectPath("/agents"));
        return text(resp.data);
      }

      // Changesets
      case "kos_submit_changeset": {
        const resp = await api.post(projectPath("/changesets"), {
          intent: args?.intent,
          filesChanged: args?.filesChanged ?? [],
          diff: args?.diff ?? "",
          agentId: args?.agentId ?? null,
          autoApplyPolicy: args?.autoApplyPolicy ?? "never",
          testsRun: args?.testsRun ?? null,
        });
        return text(resp.data);
      }

      // Locks
      case "kos_acquire_lock": {
        const resp = await api.post(projectPath("/locks"), {
          filePath: args?.filePath,
          lockType: args?.lockType ?? "write",
          durationSeconds: args?.durationSeconds ?? 300,
          agentId: args?.agentId ?? null,
        });
        return text(resp.data);
      }
      case "kos_release_lock": {
        await api.delete(projectPath(`/locks/${args?.lockId}`));
        return { content: [{ type: "text" as const, text: `Lock ${args?.lockId} released.` }] };
      }

      // Memory
      case "kos_write_memory": {
        const resp = await api.post(projectPath("/memory"), {
          title: args?.title,
          content: args?.content,
          justification: args?.justification,
          layer: args?.layer ?? "scratch",
          scopeKey: args?.scopeKey ?? null,
          tags: args?.tags ?? null,
        });
        return text(resp.data);
      }
      case "kos_search_memory": {
        const resp = await api.post(projectPath("/memory/search"), {
          query: args?.query,
          layer: args?.layer ?? null,
          limit: args?.limit ?? 5,
        });
        return text(resp.data);
      }

      // Timeline
      case "kos_get_timeline": {
        const params: Record<string, unknown> = {};
        if (args?.cursor) params["cursor"] = args.cursor;
        if (args?.limit) params["limit"] = args.limit;
        if (args?.type) params["type"] = args.type;
        const resp = await api.get(projectPath("/timeline"), { params });
        return text(resp.data);
      }

      default:
        return {
          content: [{ type: "text" as const, text: `Unknown tool: ${name}` }],
          isError: true,
        };
    }
  } catch (err: unknown) {
    const msg = err instanceof Error ? err.message : String(err);
    return {
      content: [{ type: "text" as const, text: `Error calling ${name}: ${msg}` }],
      isError: true,
    };
  }
});

// ── Start ──────────────────────────────────────────────────────────────────

function text(data: unknown) {
  return {
    content: [{ type: "text" as const, text: JSON.stringify(data, null, 2) }],
  };
}

const transport = new StdioServerTransport();
await server.connect(transport);
