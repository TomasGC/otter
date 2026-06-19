FROM ubuntu:24.04

RUN apt-get update && apt-get install -y \
    zip unzip p7zip-full python3 python3-pip tar gzip bzip2 xz-utils wget \
    && rm -rf /var/lib/apt/lists/*

# Install RAR CLI (trial - matches existing docker/rar.Dockerfile approach)
WORKDIR /tmp
RUN wget -q https://www.rarlab.com/rar/rarlinux-x64-722.tar.gz \
    && tar -xzf rarlinux-x64-722.tar.gz \
    && cp rar/rar rar/unrar /usr/local/bin/ \
    && chmod +x /usr/local/bin/rar /usr/local/bin/unrar \
    && rm -rf rarlinux-x64-722.tar.gz rar

WORKDIR /workspace
CMD ["/bin/bash"]
