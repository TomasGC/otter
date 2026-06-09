FROM ubuntu:24.04

# Install dependencies
RUN apt-get update && apt-get install -y \
    wget \
    && rm -rf /var/lib/apt/lists/*

# Download and install RAR CLI for Linux (trial version)
WORKDIR /tmp
RUN wget https://www.rarlab.com/rar/rarlinux-x64-722.tar.gz \
    && tar -xzf rarlinux-x64-722.tar.gz \
    && cp rar/rar rar/unrar /usr/local/bin/ \
    && chmod +x /usr/local/bin/rar /usr/local/bin/unrar \
    && rm -rf rarlinux-x64-722.tar.gz rar

WORKDIR /workspace

# Create RAR archive from source directory
# Usage: docker run --rm -v "$(pwd)/archives:/workspace" rar-builder test_archive.rar template/
ENTRYPOINT ["/usr/local/bin/rar", "a", "-r", "-m5"]
