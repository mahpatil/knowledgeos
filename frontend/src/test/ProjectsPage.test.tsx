import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { ProjectsPage } from '../pages/ProjectsPage'
import type { Project } from '../types'

const mockProjects: Project[] = [
  {
    id: '550e8400-e29b-41d4-a716-446655440000',
    name: 'E-Commerce Platform',
    type: 'software',
    namespace: 'project-550e8400',
    status: 'active',
    createdAt: '2026-02-19T10:00:00Z',
  },
  {
    id: '550e8400-e29b-41d4-a716-446655440001',
    name: 'Marketing Blog',
    type: 'content',
    namespace: 'project-550e8401',
    status: 'active',
    createdAt: '2026-02-19T11:00:00Z',
  },
]

vi.mock('../api/client', () => ({
  apiGet: vi.fn(),
  apiPost: vi.fn(),
}))

import * as apiClient from '../api/client'

describe('ProjectsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders project list from API response', async () => {
    vi.mocked(apiClient.apiGet).mockResolvedValue(mockProjects)

    render(
      <MemoryRouter>
        <ProjectsPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByText('E-Commerce Platform')).toBeInTheDocument()
      expect(screen.getByText('Marketing Blog')).toBeInTheDocument()
    })
  })

  it('shows empty state when no projects', async () => {
    vi.mocked(apiClient.apiGet).mockResolvedValue([])

    render(
      <MemoryRouter>
        <ProjectsPage />
      </MemoryRouter>
    )

    await waitFor(() => {
      expect(screen.getByText(/no projects/i)).toBeInTheDocument()
    })
  })

  it('create project form submits POST /api/v1/projects', async () => {
    const user = userEvent.setup()
    vi.mocked(apiClient.apiGet).mockResolvedValue([])
    vi.mocked(apiClient.apiPost).mockResolvedValue({
      id: 'new-id',
      name: 'New Project',
      type: 'software',
      namespace: 'project-new',
      status: 'active',
      createdAt: new Date().toISOString(),
    })

    render(
      <MemoryRouter>
        <ProjectsPage />
      </MemoryRouter>
    )

    await user.click(screen.getByRole('button', { name: /new project/i }))
    await user.type(screen.getByLabelText(/project name/i), 'New Project')
    await user.click(screen.getByRole('button', { name: /create/i }))

    await waitFor(() => {
      expect(apiClient.apiPost).toHaveBeenCalledWith(
        '/projects',
        expect.objectContaining({ name: 'New Project', type: 'software' })
      )
    })
  })

  it('shows validation error for missing name', async () => {
    const user = userEvent.setup()
    vi.mocked(apiClient.apiGet).mockResolvedValue([])

    render(
      <MemoryRouter>
        <ProjectsPage />
      </MemoryRouter>
    )

    await user.click(screen.getByRole('button', { name: /new project/i }))
    await user.click(screen.getByRole('button', { name: /create/i }))

    await waitFor(() => {
      expect(screen.getByText(/name is required/i)).toBeInTheDocument()
    })
  })
})
