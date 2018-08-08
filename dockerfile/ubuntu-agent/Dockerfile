FROM jenkins/jnlp-slave
MAINTAINER Raphael Yu <xianyu@microsoft.com>

USER root
RUN apt-get update && apt-get install -y --no-install-recommends \
        maven \
    && rm -rf /var/lib/apt/lists/*
USER jenkins
