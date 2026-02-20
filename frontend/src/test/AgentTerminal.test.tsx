import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, act } from '@testing-library/react'
import { AgentTerminal } from '../components/terminal/AgentTerminal'

// Mock xterm.js — it requires DOM canvas APIs not available in jsdom
vi.mock('@xterm/xterm', () => ({
  Terminal: vi.fn().mockImplementation(() => ({
    open: vi.fn(),
    write: vi.fn(),
    dispose: vi.fn(),
    onData: vi.fn(),
    loadAddon: vi.fn(),
    resize: vi.fn(),
  })),
}))

vi.mock('@xterm/addon-fit', () => ({
  FitAddon: vi.fn().mockImplementation(() => ({
    fit: vi.fn(),
    activate: vi.fn(),
  })),
}))

// Mock WebSocket
class MockWebSocket {
  static OPEN = 1
  static CLOSED = 3
  readyState = MockWebSocket.OPEN
  onopen: (() => void) | null = null
  onmessage: ((e: MessageEvent) => void) | null = null
  onclose: (() => void) | null = null
  onerror: ((e: Event) => void) | null = null
  send = vi.fn()
  close = vi.fn()
  url: string
  constructor(url: string) {
    this.url = url
    setTimeout(() => this.onopen?.(), 0)
  }
}

describe('AgentTerminal', () => {
  let originalWebSocket: typeof WebSocket

  beforeEach(() => {
    originalWebSocket = global.WebSocket
    global.WebSocket = MockWebSocket as unknown as typeof WebSocket
  })

  afterEach(() => {
    global.WebSocket = originalWebSocket
    vi.clearAllMocks()
  })

  it('opens WebSocket on mount', async () => {
    await act(async () => {
      render(
        <AgentTerminal
          projectId="proj-123"
          agentId="agent-456"
        />
      )
    })

    // Verify the WebSocket was created with correct URL
    expect(global.WebSocket).toHaveBeenCalledWith(
      expect.stringContaining('/ws/terminal/proj-123/agent-456')
    )
  })

  it('renders terminal container', async () => {
    await act(async () => {
      render(
        <AgentTerminal
          projectId="proj-123"
          agentId="agent-456"
        />
      )
    })

    expect(screen.getByTestId('terminal-container')).toBeInTheDocument()
  })

  it('cleans up WebSocket on unmount', async () => {
    let mockWsInstance: MockWebSocket | null = null
    const MockWS = vi.fn().mockImplementation((url: string) => {
      mockWsInstance = new MockWebSocket(url)
      return mockWsInstance
    })
    global.WebSocket = MockWS as unknown as typeof WebSocket

    const { unmount } = render(
      <AgentTerminal
        projectId="proj-123"
        agentId="agent-456"
      />
    )

    await act(async () => {
      await new Promise(r => setTimeout(r, 10))
    })

    unmount()

    expect(mockWsInstance?.close).toHaveBeenCalled()
  })
})
