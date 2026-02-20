export interface Project {
  id: string
  name: string
  type: 'software' | 'content' | 'marketing' | 'migration' | 'research'
  namespace: string
  status: string
  createdAt: string
}

export interface Workspace {
  id: string
  name: string
  type: 'code' | 'content' | 'legacy' | 'target' | 'mapping'
  mode: 'read-write' | 'read-only'
  path: string
  pvcName: string
  fileTree: string[]
}

export interface Agent {
  id: string
  name: string
  model: 'claude' | 'codex'
  role: string
  status: 'pending' | 'running' | 'stopped' | 'failed' | 'terminated'
  podName: string
  createdAt: string
}

export interface ChangeSet {
  id: string
  agentId: string
  intent: string
  filesChanged: string[]
  diff: string
  status: 'pending' | 'auto_applied' | 'agent_review' | 'human_review' | 'approved' | 'rejected' | 'rolled_back'
  validatorResults?: {
    passed: boolean
    failures: string[]
    durationMs: number
  }
  createdAt: string
}

export interface MemoryEntry {
  id: string
  title: string
  content: string
  justification: string
  layer: 'canonical' | 'feature' | 'scratch'
  scopeKey?: string
  tags?: string[]
  score?: number
  createdAt: string
  expiresAt?: string
}

export interface FileLock {
  id: string
  filePath: string
  lockType: 'read' | 'write'
  lockedBy: string
  expiresAt: string
  createdAt: string
}

export interface TimelineEvent {
  id: string
  type: string
  payload: Record<string, unknown>
  reversible: boolean
  replayCmd?: string
  agentId?: string
  createdAt: string
}

export interface CreateProjectRequest {
  name: string
  type: Project['type']
  templateHint?: string
}

export interface CreateAgentRequest {
  name: string
  model: Agent['model']
  role: string
  permissions?: Record<string, unknown>
  prompt?: string
  workspaceId?: string
}
