FROM ubuntu:latest
LABEL authors="dimfatal"

ENTRYPOINT ["top", "-b"]