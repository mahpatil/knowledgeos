FROM ubuntu:24.04

# Prevent interactive prompts during package installation
ENV DEBIAN_FRONTEND=noninteractive
ENV TERM=xterm-256color

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    tmux \
    git \
    curl \
    wget \
    jq \
    ca-certificates \
    gnupg \
    lsb-release \
    vim \
    nano \
    zip \
    unzip \
    && rm -rf /var/lib/apt/lists/*

# Install Node.js 22
RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
    && apt-get install -y nodejs \
    && rm -rf /var/lib/apt/lists/*

# Install GraalVM 21 (for agent builds)
ENV GRAALVM_VERSION=21
ENV GRAALVM_HOME=/opt/graalvm
RUN curl -fsSL https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-21.0.2/graalvm-community-jdk-21.0.2_linux-x64_bin.tar.gz \
    -o /tmp/graalvm.tar.gz \
    && mkdir -p /opt/graalvm \
    && tar -xzf /tmp/graalvm.tar.gz --strip-components=1 -C /opt/graalvm \
    && rm /tmp/graalvm.tar.gz
ENV JAVA_HOME=/opt/graalvm
ENV PATH=$JAVA_HOME/bin:$PATH

# Install Claude CLI
RUN npm install -g @anthropic-ai/claude-code 2>/dev/null || true

# Install OpenAI Codex CLI
RUN npm install -g @openai/codex 2>/dev/null || true

# Create non-root agent user
RUN useradd -m -u 1000 -s /bin/bash agent
USER agent
WORKDIR /home/agent

# Default tmux config for persistent sessions
COPY --chown=agent:agent tmux.conf /home/agent/.tmux.conf

# Startup script — creates persistent tmux session named "main"
COPY --chown=agent:agent entrypoint.sh /home/agent/entrypoint.sh
RUN chmod +x /home/agent/entrypoint.sh

CMD ["/home/agent/entrypoint.sh"]
