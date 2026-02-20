#!/bin/bash
set -e

# Create a persistent tmux session named "main"
# The backend TerminalGatewayService will attach to this session via kubectl exec.

SESSION_NAME="main"

# Start tmux session with initial working directory
tmux new-session -d -s "$SESSION_NAME" -c "${WORKSPACE_PATH:-/home/agent}"

# Set environment variables inside the tmux session
tmux send-keys -t "$SESSION_NAME" "export AGENT_ID=${AGENT_ID}" Enter
tmux send-keys -t "$SESSION_NAME" "export AGENT_ROLE=${AGENT_ROLE}" Enter
tmux send-keys -t "$SESSION_NAME" "export PROJECT_ID=${PROJECT_ID}" Enter
tmux send-keys -t "$SESSION_NAME" "export BACKEND_URL=${BACKEND_URL}" Enter

# Change to workspace directory
if [ -n "$WORKSPACE_PATH" ] && [ -d "$WORKSPACE_PATH" ]; then
    tmux send-keys -t "$SESSION_NAME" "cd ${WORKSPACE_PATH}" Enter
fi

echo "Agent pod started: AGENT_ID=${AGENT_ID} ROLE=${AGENT_ROLE} MODEL=${MODEL}"
echo "tmux session '${SESSION_NAME}' ready for terminal attachment"

# Keep the container alive by waiting on tmux
tmux wait-for -S done || true

# Fallback: sleep forever
while true; do sleep 3600; done
